package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.STALL_TIMEOUT_MS
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerErrorEvent
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.GeneratorEvent
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.player.speaker.SpeakerEvent
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.sage.logging.Hatchet

private const val SKIP_BACK_THRESHOLD_MS = 3_000L

/** When "shuffle skips jingles/SFX" is on, shuffled setlists drop tracks shorter than this so
 *  short stings don't ambush the listener mid-shuffle. */
private const val MIN_SHUFFLE_TRACK_MS = 10_000L

/** Consecutive generator errors (with no successful audio in between) before the director
 *  gives up and stops the session instead of skipping to yet another track. */
private const val MAX_CONSECUTIVE_FAILURES = 3

private const val MILLIS_PER_SECOND = 1_000L

/**
 * Production [Director] implementation.
 *
 * ### Model / reduce / effects
 * All playback state lives in a single immutable [Model] (the public [ChipboxPlaybackState] plus
 * the internal bookkeeping the state machine needs — session, setlist, failure streak, render
 * watermark). Generator and speaker events are merged into one stream and fed through pure
 * `reduce(model, event)` functions that return `(nextModel, effects)`: the next model and a list
 * of [Effect]s describing the I/O to perform (start/stop a track, switch the speaker, arm the
 * watchdog, emit metadata, …). [commit] publishes the new model; [apply] performs the effects in
 * order. Reducers issue no I/O and launch nothing themselves — which is what keeps track-advance
 * readable as data (`TrackChange` is "last → ENDING + stop, else → advance + start track") and
 * makes them unit-testable by asserting the returned effect list.
 *
 * Transport methods ([play], [pause], [seek], [skipForward], …) are imperative entry points: they
 * update the model and call the generator/speaker directly, since they're driven by the UI rather
 * than the event stream.
 *
 * Setlist resolution is driven by [Session.type]: `GAME`, `ARTIST`, and `ALL_TRACKS` sessions pull
 * tracks for the given scope from the repository; `PLAYLIST` is not yet implemented.
 *
 * ### Threading
 * The default [dispatcher] is single-threaded ([CoroutineDispatcher.limitedParallelism]`(1)` over
 * [Dispatchers.Default]). A single consumer processes one event at a time, and the single thread
 * keeps transport methods from racing the consumer, so the unguarded [model] mutations are safe
 * without a lock. Tests inject a single-threaded test dispatcher, which models the same guarantee.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealDirector(
    private val generator: Generator,
    private val speaker: Speaker,
    private val repository: Repository,
    private val settingsManager: ChipboxSettingsManager,
    private val hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) : Director {
    private val directorScope = CoroutineScope(SupervisorJob() + dispatcher)

    fun release() {
        directorScope.cancel()
    }

    /** The complete state of the playback machine. [playback] is the public, UI-facing slice; the
     *  rest is internal bookkeeping the reducers thread through so they stay pure. Visible to tests
     *  so reducers can be exercised directly by asserting the returned (model, effects). */
    internal data class Model(
        val playback: ChipboxPlaybackState,
        val session: Session?,
        val setlist: List<Long>?,
        /** Generator errors since the last successful audio emission; drives the give-up cutoff. */
        val consecutiveFailures: Int,
        /** Highest render watermark (ms) seen for the current track, so a render that's still
         *  advancing (healthy uncached seek) can be told apart from a wedged one. */
        val lastRenderProgressMs: Long,
        /** Set by [restore]: a saved offset (ms) to resume a restored-but-paused session at.
         *  While non-null the session is "loaded, paused, waiting for the user to press play" —
         *  the first buffer lands in PAUSED instead of PLAYING, the paused position display shows
         *  this value, and the first [play] seeks here before starting the speaker. Cleared the
         *  moment the restore is consumed (play) or invalidated (new session / track jump / seek). */
        val pendingResumeMs: Long? = null,
    )

    private var model = Model(
        playback = ChipboxPlaybackState(
            state = PlayerState.IDLE,
            position = 0L,
            generatorProducedMs = 0L,
            playbackSpeed = 1.0f,
            skipForwardAllowed = false,
            errorMessage = null,
        ),
        session = null,
        setlist = null,
        consecutiveFailures = 0,
        lastRenderProgressMs = 0L,
    )

    /** The I/O a reducer asks for. Reducers return these instead of performing them, so they stay
     *  pure and [apply] can run the sequence in order on the consumer. Visible to tests. */
    internal sealed interface Effect {
        data class StartTrack(val trackId: Long) : Effect
        data object StopGenerator : Effect
        data object StopSpeaker : Effect
        data object SpeakerPlay : Effect
        data class SwitchSpeaker(val trackId: Long) : Effect
        data object ArmWatchdog : Effect
        data object CancelWatchdog : Effect
        data class EmitMetadata(val track: Track) : Effect
        data class PublishError(val message: String, val trackId: Long?) : Effect
    }

    private fun Model.with(vararg effects: Effect): Pair<Model, List<Effect>> = this to effects.asList()

    /** Pending [STALL_TIMEOUT_MS] timer. Re-armed on every progress signal (audio produced, or the
     *  render watermark advancing); fires only if the generator goes silent for the whole window
     *  while audio is meant to be flowing. Null while disarmed (paused, stopped, ending, idle). */
    private var stallWatchdogJob: Job? = null

    // replay = 1 so late subscribers (e.g. a screen opened mid-playback) immediately
    // receive the current track / state instead of waiting for the next change.
    private val metadataStateMutable = MutableSharedFlow<Track?>(replay = 1)

    private val playbackStateMutable = MutableSharedFlow<ChipboxPlaybackState>(replay = 1)

    // replay = 1 so a debug screen opened mid-playback receives the active session immediately.
    private val sessionStateMutable = MutableSharedFlow<Session?>(replay = 1)

    // No replay: the error log is for errors that happen while a screen is watching, not a
    // backlog replayed to late subscribers. DROP_OLDEST keeps a burst of rapid failures flowing.
    private val errorEventsMutable = MutableSharedFlow<PlayerErrorEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // The stall watchdog feeds a synthetic generator Error in here rather than touching the model
    // directly, so it joins the same single-consumer pipeline and can't race it.
    private val internalEvents = MutableSharedFlow<GeneratorEvent>(extraBufferCapacity = 4)

    private sealed interface Input {
        data class Gen(val event: GeneratorEvent) : Input
        data class Spk(val event: SpeakerEvent) : Input
    }

    init {
        // Seed each replay buffer so a subscriber that attaches before any playback has
        // happened gets a meaningful "nothing playing" emission instead of hanging on an
        // empty flow.
        metadataStateMutable.tryEmit(null)
        playbackStateMutable.tryEmit(model.playback)
        sessionStateMutable.tryEmit(null)

        // One consumer for both event sources (plus the watchdog's internal events), so events are
        // processed strictly one at a time and never interleave on the model.
        directorScope.launch {
            merge(
                merge(generator.events().distinctUntilChanged(), internalEvents).map { Input.Gen(it) },
                speaker.events().distinctUntilChanged().map { Input.Spk(it) },
            ).collect(::process)
        }
    }

    private suspend fun process(input: Input) {
        val before = model.playback.state
        val (nextModel, effects) = when (input) {
            is Input.Gen -> reduce(model, input.event)
            is Input.Spk -> reduce(model, input.event)
        }
        commit(nextModel)
        apply(effects)
        traceTransition(input, before, model.playback.state, effects)
    }

    /**
     * One debug line per processed event: the event, the player-state transition, and the effects
     * the reducer decided to run — the director's orchestration view, to correlate against the
     * generator's and speaker's own logs. Skips the per-buffer heartbeats (an Emitting/Rendering
     * that neither moved the state nor issued anything beyond (re)arming the watchdog) so the trace
     * stays signal rather than spam.
     */
    private fun traceTransition(
        input: Input,
        before: PlayerState,
        after: PlayerState,
        effects: List<Effect>,
    ) {
        val onlyWatchdog = effects.all { it == Effect.ArmWatchdog || it == Effect.CancelWatchdog }
        if (before == after && onlyWatchdog) return
        val event = when (input) {
            is Input.Gen -> input.event
            is Input.Spk -> input.event
        }
        hatchet.d("reduce($event): $before -> $after; effects=${effects.map { it.label() }}")
    }

    /** Compact log label — keeps [Effect.EmitMetadata] from dumping a whole [Track] into the line. */
    private fun Effect.label(): String = when (this) {
        is Effect.StartTrack -> "StartTrack($trackId)"
        Effect.StopGenerator -> "StopGenerator"
        Effect.StopSpeaker -> "StopSpeaker"
        Effect.SpeakerPlay -> "SpeakerPlay"
        is Effect.SwitchSpeaker -> "SwitchSpeaker($trackId)"
        Effect.ArmWatchdog -> "ArmWatchdog"
        Effect.CancelWatchdog -> "CancelWatchdog"
        is Effect.EmitMetadata -> "EmitMetadata(${track.id})"
        is Effect.PublishError -> "PublishError"
    }

    /** Publish [next] as the live model: stamp the speaker's position onto the playback slice (so
     *  pause/resume and track-change reports anchor the notification's progress bar to actual
     *  played audio) and re-emit the playback (and, when it changed, the session). */
    private fun commit(next: Model) {
        val sessionChanged = model.session != next.session
        // While a restore is pending the speaker hasn't played a frame (position 0), so anchor the
        // paused progress bar to the saved offset until the first play() seeks there for real.
        val position = next.pendingResumeMs ?: speaker.currentPositionMs()
        val stampedPlayback = next.playback.copy(position = position)
        model = next.copy(playback = stampedPlayback)
        directorScope.launch { playbackStateMutable.emit(stampedPlayback) }
        if (sessionChanged) {
            directorScope.launch { sessionStateMutable.emit(next.session) }
        }
    }

    private suspend fun apply(effects: List<Effect>) {
        effects.forEach { effect ->
            when (effect) {
                is Effect.StartTrack -> generator.startTrack(effect.trackId)
                Effect.StopGenerator -> generator.stop()
                Effect.StopSpeaker -> speaker.stop()
                Effect.SpeakerPlay -> speaker.play()
                is Effect.SwitchSpeaker -> speaker.switchTo(effect.trackId)
                Effect.ArmWatchdog -> armStallWatchdog()
                Effect.CancelWatchdog -> cancelStallWatchdog()
                is Effect.EmitMetadata -> metadataStateMutable.emit(effect.track)
                is Effect.PublishError -> publishError(effect.message, effect.trackId)
            }
        }
    }

    override fun request(request: SessionRequest) {
        when (request) {
            is SessionRequest.Start -> start(request.session)
            is SessionRequest.StartSetlist -> with(request) { start(setlist, startingPosition, sourceName, shuffled) }
            is SessionRequest.Restore -> restore(request.session, request.positionMs)
            SessionRequest.Play -> play()
            SessionRequest.Pause -> pause()
            SessionRequest.Stop -> stop()
            is SessionRequest.Seek -> seek(request.positionMs)
            SessionRequest.SkipForward -> skipForward()
            SessionRequest.SkipBack -> skipBack()
            is SessionRequest.SetShuffled -> setShuffled(request.shuffled)
            is SessionRequest.SetRepeatMode -> setRepeatMode(request.mode)
            SessionRequest.PauseTemporarily -> pauseTemporarily()
            SessionRequest.Duck -> duck()
            SessionRequest.ResumeFocus -> resumeFocus()
            is SessionRequest.SetVolume -> setVolume(request.scale)
        }
    }

    private fun start(session: Session) {
        directorScope.launch {
            // A fresh session must take over cleanly rather than queue behind whatever the
            // generator is currently doing. If we only queued the new track, a generator stuck
            // rendering a slow or silent track would hold it in its channel while the model (and
            // the stall watchdog) have already moved on — so the stuck track's eventual failure
            // gets misattributed to, and skips, the track the user just asked for. When audio was
            // flowing, abandon the in-flight render so the model and the generator stay in sync.
            val wasActive = model.playback.state.hasActiveAudio()
            if (wasActive) {
                cancelStallWatchdog()
                generator.stop()
            }

            val setlistForSession = getSetlistForSession(session)
                .let { if (session.shuffled) it.shuffled() else it }

            commit(
                model.copy(
                    session = session,
                    setlist = setlistForSession,
                    consecutiveFailures = 0,
                    // A fresh user-initiated session supersedes any pending restore.
                    pendingResumeMs = null,
                ),
            )

            val firstTrackId = when {
                session.startingTrackId != null -> session.startingTrackId

                session.currentPosition != null -> setlistForSession[session.currentPosition!!]

                session.startingPosition != null -> setlistForSession[session.startingPosition!!]

                // SINGLE_TRACK is a self-contained "play this one track" session — contentId is
                // already the track id, no position hint needed from the caller. Default to
                // position 0 so the rest of the start flow has a valid index into the 1-item setlist.
                session.type == SessionType.SINGLE_TRACK -> session.contentId

                else -> {
                    emitError("Unable to find a track id to play.")
                    return@launch
                }
            }

            if (firstTrackId != null) {
                val startingPosition = session.startingPosition
                    ?: setlistForSession.indexOfFirst { it == firstTrackId }

                commit(model.copy(session = session.copy(currentPosition = startingPosition)))
                generator.startTrack(firstTrackId)
                if (wasActive) {
                    // Cut the speaker over to the new track, discarding any audio still queued from
                    // the outgoing session and restarting the consume loop on the new track.
                    // Skipped on a cold start, where the consume loop isn't running and the buffer
                    // manager isn't initialised until the generator's first setSampleRate lands.
                    speaker.switchTo(firstTrackId)
                }
            }
        }
    }

    private fun restore(session: Session, positionMs: Long) {
        directorScope.launch {
            val setlistForSession = getSetlistForSession(session)
                .let { if (session.shuffled) it.shuffled() else it }

            if (setlistForSession.isEmpty()) {
                hatchet.w("restore: session resolved to an empty setlist; nothing to restore.")
                return@launch
            }

            // Mirror start()'s starting-track resolution, but record the saved offset so the first
            // buffer lands paused (see reduceGeneratorEmitting) and the first play() seeks here.
            commit(
                model.copy(
                    session = session,
                    setlist = setlistForSession,
                    consecutiveFailures = 0,
                    pendingResumeMs = positionMs.coerceAtLeast(0L),
                ),
            )

            val firstTrackId = when {
                session.startingTrackId != null -> session.startingTrackId
                session.currentPosition != null -> setlistForSession.getOrNull(session.currentPosition!!)
                session.startingPosition != null -> setlistForSession.getOrNull(session.startingPosition!!)
                session.type == SessionType.SINGLE_TRACK -> session.contentId
                else -> null
            }

            if (firstTrackId == null) {
                hatchet.w("restore: could not resolve a track to restore; ignoring.")
                commit(model.copy(session = null, setlist = null, pendingResumeMs = null))
                return@launch
            }

            val startingPosition = setlistForSession.indexOfFirst { it == firstTrackId }
                .takeIf { it >= 0 } ?: 0
            commit(model.copy(session = session.copy(currentPosition = startingPosition)))
            generator.startTrack(firstTrackId)
        }
    }

    private fun PlayerState.hasActiveAudio(): Boolean = when (this) {
        PlayerState.PLAYING, PlayerState.BUFFERING, PlayerState.PAUSED, PlayerState.ENDING -> true
        PlayerState.IDLE, PlayerState.STOPPED, PlayerState.ERROR -> false
    }

    /** States where the generator is expected to be feeding audio, so a gap in its output is a
     *  fault worth a stall guard. PAUSED/STOPPED/ENDING/ERROR/IDLE expect silence, so the guard
     *  stays off there. */
    private fun PlayerState.expectsAudioFlow(): Boolean =
        this == PlayerState.PLAYING || this == PlayerState.BUFFERING

    private fun start(
        setlist: List<Long>,
        startingPosition: Int,
        sourceName: String?,
        shuffled: Boolean,
    ) {
        start(
            Session(
                type = SessionType.SETLIST,
                contentId = 0L,
                explicitSetlist = setlist,
                sourceName = sourceName,
                startingPosition = startingPosition,
                shuffled = shuffled,
            )
        )
    }

    private fun play() {
        directorScope.launch {
            when (model.playback.state) {
                PlayerState.PAUSED -> {
                    val resumeMs = model.pendingResumeMs
                    if (resumeMs != null) {
                        // First play of a restored session: jump to the saved offset before any
                        // audio is heard. generator.seek repositions the source; speaker.seek
                        // drains the buffer the generator pre-filled at position 0 and starts the
                        // consume loop — resume and seek in one step. Clearing pendingResumeMs
                        // makes every later pause/resume behave normally.
                        commit(
                            model.copy(
                                pendingResumeMs = null,
                                playback = model.playback.copy(state = PlayerState.PLAYING),
                            ),
                        )
                        generator.seek(resumeMs)
                        speaker.seek()
                        armStallWatchdog()
                    } else {
                        speaker.play()
                        commit(model.copy(playback = model.playback.copy(state = PlayerState.PLAYING)))
                        generator.play()
                        // Audio should resume flowing, so guard for a stall again until it does.
                        armStallWatchdog()
                    }
                }

                // A finished setlist ends in STOPPED with the generator loop and speaker sink both
                // torn down, but the session/setlist still point at the last track the UI is
                // showing. There's nothing queued for a bare generator.play() to pick up — its loop
                // would just block on an empty channel forever — so re-issue the current track from
                // the top, matching the user's expectation that play restarts the track on screen.
                PlayerState.STOPPED -> restartCurrentTrack()

                else -> generator.play()
            }
        }
    }

    /**
     * Reload and restart the track at the current setlist position from the beginning. Unlike a
     * paused resume there's nothing to un-pause — after a setlist completes the generator and
     * speaker have been torn down — so the track has to be queued afresh, which re-spins both the
     * generation and consume loops (and walks the state back through BUFFERING -> PLAYING via the
     * usual Loading/Emitting reducers). No-op if there's no session to restart.
     */
    private suspend fun restartCurrentTrack() {
        val trackId = model.session?.currentPosition?.let { model.setlist?.getOrNull(it) }
        if (trackId == null) {
            hatchet.w("play() while STOPPED but no current track to restart.")
            return
        }
        commit(model.copy(consecutiveFailures = 0))
        generator.startTrack(trackId)
    }

    /** (Re)start the stall timer. Called on every progress signal while audio should be flowing,
     *  so a healthy stream — or a render-ahead wait that keeps advancing — perpetually pushes the
     *  deadline out and never trips. When it fires it feeds a synthetic generator error into the
     *  consumer, so a stall is handled by the same skip-or-stop policy as any other bad track. */
    private fun armStallWatchdog() {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null
        // Only guard while audio is meant to be flowing. After a pause the generator keeps emitting
        // for a moment as it drains its render-ahead buffer; those Emittings must not re-arm the
        // watchdog, or it fires partway through an intentional pause and "recovers" by skipping to
        // the next track.
        if (!model.playback.state.expectsAudioFlow()) return
        stallWatchdogJob = directorScope.launch {
            delay(STALL_TIMEOUT_MS)
            hatchet.w(
                "Generator made no progress for $STALL_TIMEOUT_MS ms; " +
                    "treating the current track as stalled."
            )
            internalEvents.emit(
                GeneratorEvent.Error(
                    "Playback stalled: no audio for ${STALL_TIMEOUT_MS / MILLIS_PER_SECOND} seconds."
                )
            )
        }
    }

    /** Stop guarding for a stall (paused / stopped / ending / errored — anywhere audio isn't
     *  expected to flow, so silence is normal rather than a fault). */
    private fun cancelStallWatchdog() {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null
    }

    private fun pause() {
        directorScope.launch {
            // Paused audio is meant to be silent — don't let that read as a stall. (The generator
            // keeps running until its buffers back up, so it isn't stopped here.)
            speaker.pause()
            commit(model.copy(playback = model.playback.copy(state = PlayerState.PAUSED)))
            // Cancel AFTER committing PAUSED: a generator Emitting can slip in while speaker.pause()
            // suspends and re-arm the guard (we were still PLAYING then). With PAUSED now committed,
            // clear it here — and armStallWatchdog() won't re-arm while paused.
            cancelStallWatchdog()
        }
    }

    private fun stop() {
        directorScope.launch {
            cancelStallWatchdog()
            speaker.stop()
            generator.stop()
            commit(model.copy(playback = model.playback.copy(state = PlayerState.STOPPED)))
        }
    }

    private fun seek(positionMs: Long) {
        directorScope.launch {
            // An explicit seek supersedes a pending restore offset (the user chose a new spot).
            if (model.pendingResumeMs != null) commit(model.copy(pendingResumeMs = null))
            generator.seek(positionMs)
            speaker.seek()
        }
    }

    private fun skipForward() {
        directorScope.launch {
            val session = model.session ?: return@launch
            val setlist = model.setlist ?: return@launch
            val atLast = isLast(session, setlist)
            // Honour the same gate the UI is showing (skipForwardAllowed): at the end of the
            // setlist there's nowhere to go unless repeat-all is on, in which case we wrap to the top.
            if (atLast && !wrapsAtEnd(session, setlist)) return@launch

            val nextPosition = if (atLast) 0 else (session.currentPosition ?: -1) + 1
            hatchet.i("skipForward: advancing to position $nextPosition (state=${model.playback.state}).")
            switchToTrack(model.copy(session = session.copy(currentPosition = nextPosition)), setlist[nextPosition])
        }
    }

    private fun skipBack() {
        directorScope.launch {
            val session = model.session ?: return@launch
            val setlist = model.setlist ?: return@launch
            val withinTrackPosition = model.playback.position
            val setlistPosition = session.currentPosition ?: 0

            if (withinTrackPosition > SKIP_BACK_THRESHOLD_MS || setlistPosition <= 0) {
                // Restart of the current track — no track change, so a plain in-track seek.
                if (model.pendingResumeMs != null) commit(model.copy(pendingResumeMs = null))
                generator.seek(0L)
                speaker.seek()
                return@launch
            }

            val previousPosition = setlistPosition - 1
            switchToTrack(model.copy(session = session.copy(currentPosition = previousPosition)), setlist[previousPosition])
        }
    }

    /**
     * Cleanly switch the generator and speaker onto [trackId] for a user-initiated jump (skip
     * forward/back), with [advanced] holding the model whose session position has already moved to
     * the new track. Like [start]'s handoff, it abandons any in-flight render first — so a generator
     * stuck on a slow/silent track can't hold the new track in its channel while the model moves on,
     * which would misattribute the stuck track's failure to the jumped-to track — then cuts the
     * speaker over, discarding the outgoing track's queued audio.
     */
    private suspend fun switchToTrack(advanced: Model, trackId: Long) {
        cancelStallWatchdog()
        generator.stop()
        // Jumping to a different track invalidates any pending restore offset (it was for the
        // track we're leaving), so the next play() resumes the new track from the top.
        commit(advanced.copy(pendingResumeMs = null))
        generator.startTrack(trackId)
        speaker.switchTo(trackId)
    }

    private fun setShuffled(shuffled: Boolean) {
        directorScope.launch {
            val session = model.session ?: return@launch
            if (session.shuffled == shuffled) return@launch

            // Remember which track is playing so we can find it in the new ordering.
            val playingTrackId = model.setlist
                ?.let { setlist -> session.currentPosition?.let(setlist::getOrNull) }

            val newSetlist = getSetlistForSession(session)
                .let { if (shuffled) it.shuffled() else it }
            val newPosition = playingTrackId
                ?.let { id -> newSetlist.indexOf(id).takeIf { it >= 0 } }
                ?: 0

            commit(
                model.copy(
                    setlist = newSetlist,
                    session = session.copy(shuffled = shuffled, currentPosition = newPosition),
                )
            )
        }
    }

    private fun setRepeatMode(mode: RepeatMode) {
        directorScope.launch {
            val session = model.session ?: return@launch
            if (session.repeatMode == mode) return@launch

            // Repeat is consulted lazily on the next track change, so this neither touches the
            // setlist nor interrupts the current track. The one immediate effect is the
            // skip-forward gate: repeat-all makes "next" available even on the last track (it
            // wraps), so recompute and re-publish it as part of the same commit.
            val updated = session.copy(repeatMode = mode)
            val setlist = model.setlist
            val skipAllowed = setlist
                ?.let { skipForwardAllowed(updated, it) }
                ?: model.playback.skipForwardAllowed
            commit(
                model.copy(
                    session = updated,
                    playback = model.playback.copy(skipForwardAllowed = skipAllowed),
                )
            )
        }
    }

    override fun metadataState() = metadataStateMutable.asSharedFlow()

    override fun playbackState() = playbackStateMutable.asSharedFlow()

    override fun sessionState() = sessionStateMutable.asSharedFlow()

    override fun errorEvents() = errorEventsMutable.asSharedFlow()

    private fun pauseTemporarily() {
        directorScope.launch {
            // A transient audio-focus loss stops the consume loop just like a real pause, so
            // reflect it as PAUSED (the UI was previously left showing PLAYING with no audio).
            speaker.pause()
            commit(model.copy(playback = model.playback.copy(state = PlayerState.PAUSED)))
            // Cancel after committing PAUSED (see pause()).
            cancelStallWatchdog()
        }
    }

    /**
     * 🦆 Drop the speaker's output to 50% but keep playing — the OS only asked us to get out
     * of the way of a transient sound, not to stop.
     */
    private fun duck() {
        speaker.setDucked(true)
    }

    private fun resumeFocus() {
        directorScope.launch {
            // Undo a duck() (no-op if we weren't ducked) and restart the consume loop if a
            // pauseTemporarily() had stopped it (no-op if it's already running).
            speaker.setDucked(false)
            speaker.play()
            // Counterpart to pauseTemporarily()'s PAUSED: if a transient focus loss had paused us,
            // come back to PLAYING and guard for stalls again. State-wise a no-op if we were only
            // ducked (already PLAYING).
            if (model.playback.state == PlayerState.PAUSED) {
                commit(model.copy(playback = model.playback.copy(state = PlayerState.PLAYING)))
                armStallWatchdog()
            }
        }
    }

    private fun setVolume(scale: Double) {
        speaker.setVolume(scale)
    }

    private fun isLast(session: Session, setlist: List<Long>): Boolean {
        val nextTrackPosition = (session.currentPosition ?: -1) + 1
        return nextTrackPosition >= setlist.size
    }

    /** True when the end of the setlist loops back to the start rather than stopping — i.e.
     *  repeat-all over a non-empty setlist. (Repeat-one restarts the current track in place and
     *  never reaches the end, so it doesn't "wrap" in this sense.) */
    private fun wrapsAtEnd(session: Session, setlist: List<Long>): Boolean =
        session.repeatMode == RepeatMode.ALL && setlist.isNotEmpty()

    /** Whether "skip forward" has somewhere to go: another track ahead, or a wrap to the top when
     *  repeat-all is on. Drives both the UI's enabled state and the [skipForward] no-op guard. */
    private fun skipForwardAllowed(session: Session, setlist: List<Long>): Boolean =
        !isLast(session, setlist) || wrapsAtEnd(session, setlist)

    /** Track id the director currently considers active, from the live setlist position.
     *  Null until a session + setlist are established. */
    private fun currentTrackId(m: Model): Long? {
        val position = m.session?.currentPosition ?: return null
        return m.setlist?.getOrNull(position)
    }

    private suspend fun getSetlistForSession(session: Session): List<Long> {
        // Only filter when the user is actually shuffling and has opted in. Non-shuffled playback
        // (and explicit/single-track setlists, which we have no lengths for here) is untouched.
        val skipShort = session.shuffled && settingsManager.getShuffleSkipsShortTracks().first()
        return when (session.type) {
            SessionType.GAME -> getTrackListForGame(session.contentId, skipShort)
            SessionType.ARTIST -> getTrackListForArtist(session.contentId, skipShort)
            SessionType.PLAYLIST -> getTrackListForPlaylist(session.contentId)
            SessionType.ALL_TRACKS -> getTrackListForAllTracks(skipShort)
            SessionType.PLATFORM -> getTrackListForPlatform(session.contentId, skipShort)
            SessionType.SETLIST -> session.explicitSetlist.orEmpty()
            SessionType.SINGLE_TRACK -> listOf(session.contentId)
        }
    }

    // Drops sub-[MIN_SHUFFLE_TRACK_MS] tracks when [skipShort], but never to nothing: a library (or
    // game) made entirely of short stings would otherwise yield an empty setlist and stall playback,
    // so we fall back to the unfiltered list in that case.
    private fun List<Track>.toSetlistIds(skipShort: Boolean): List<Long> {
        val kept = if (skipShort) filter { it.trackLengthMs >= MIN_SHUFFLE_TRACK_MS } else this
        return kept.ifEmpty { this }.map { it.id }
    }

    private suspend fun getTrackListForPlatform(contentId: Long, skipShort: Boolean) = repository
        .getTracksForPlatform(Platform.entries[contentId.toInt()])
        .toSetlistIds(skipShort)

    private suspend fun getTrackListForGame(gameId: Long, skipShort: Boolean) = repository
        .getTracksForGame(gameId)
        .toSetlistIds(skipShort)

    // Order (A–Z by game title) is owned by the DAO query, so it matches the artist-detail screen
    // without hydrating each track's Game here — we only need the ids.
    private suspend fun getTrackListForArtist(artistId: Long, skipShort: Boolean) = repository
        .getTracksForArtist(artistId)
        .toSetlistIds(skipShort)

    private fun getTrackListForPlaylist(playlistId: Long): List<Long> {
        TODO("Not yet implemented")
    }

    private suspend fun getTrackListForAllTracks(skipShort: Boolean): List<Long> = repository
        .getAllTracks(withGame = false, withArtists = false)
        .filter { it is Data.Succeeded }
        .map { (it as Data.Succeeded).data }
        .first()
        .toSetlistIds(skipShort)

    // ---- generator-event reducers ----

    internal suspend fun reduce(m: Model, event: GeneratorEvent): Pair<Model, List<Effect>> = when (event) {
        is GeneratorEvent.Error -> reduceGeneratorError(m, event)
        is GeneratorEvent.Loading -> reduceGeneratorLoading(m, event)
        is GeneratorEvent.Emitting -> reduceGeneratorEmitting(m, event)
        is GeneratorEvent.Rendering -> reduceGeneratorRendering(m, event)
        GeneratorEvent.TrackChange -> reduceTrackChange(m)
    }

    /**
     * The generator is waiting on the writer to render past the play cursor (e.g. an uncached
     * seek). Treat an advancing watermark as liveness: re-arm the stall watchdog whenever
     * [GeneratorEvent.Rendering.cachedMs] climbs, and leave it running (counting down) when it
     * doesn't — so a wedged render trips the guard but a slow-but-progressing one never does.
     * Also surface the growing cache to the now-playing UI.
     */
    private fun reduceGeneratorRendering(
        m: Model,
        event: GeneratorEvent.Rendering,
    ): Pair<Model, List<Effect>> {
        val withCache = m.copy(playback = m.playback.copy(cachedMs = event.cachedMs))
        return if (event.cachedMs > m.lastRenderProgressMs) {
            withCache.copy(lastRenderProgressMs = event.cachedMs).with(Effect.ArmWatchdog)
        } else {
            withCache.with()
        }
    }

    private suspend fun reduceGeneratorLoading(
        m: Model,
        event: GeneratorEvent.Loading,
    ): Pair<Model, List<Effect>> {
        val session = m.session
        val setlist = m.setlist
        if (session == null || setlist == null) {
            val detail = if (session == null) "Invalid session." else "Invalid setlist."
            hatchet.e("Error: $detail")
            return m.copy(
                playback = m.playback.copy(
                    state = PlayerState.ERROR,
                    errorMessage = "Unable to determine if next track available.",
                ),
            ).with(Effect.CancelWatchdog, Effect.PublishError(detail, currentTrackId(m)))
        }

        // A new track is loading: we now expect audio, so start guarding for a stall and reset the
        // render-progress mark this track will be measured against.
        val armed = m.copy(lastRenderProgressMs = 0L)
        val skipForwardAllowed = skipForwardAllowed(session, setlist)

        // Already mid-playback (audio flowing) or mid-buffer (starved): a track change is in
        // flight. Don't force a state — the speaker decides PLAYING vs BUFFERING by whether audio
        // keeps flowing, and the now-playing metadata updates when the new track's first buffer
        // plays (SpeakerEvent.TrackChange). Just reset the high-water mark, cache progress, and
        // skip-forward gate.
        if (m.playback.state == PlayerState.PLAYING || m.playback.state == PlayerState.BUFFERING) {
            hatchet.i(
                "handleGeneratorLoading(track=${event.trackId}): " +
                    "track change while ${m.playback.state}; awaiting audio."
            )
            return armed.copy(
                playback = m.playback.copy(
                    generatorProducedMs = 0L,
                    cachedMs = 0L,
                    skipForwardAllowed = skipForwardAllowed,
                ),
            ).with(Effect.ArmWatchdog)
        }

        // Nothing playing yet (cold start / resumed from a stopped-ish state): show this track's
        // metadata and wait for the first buffer.
        val newTrack = getTrack(event.trackId) ?: return metadataLoadError(m, event.trackId)

        // A restored session loads straight to PAUSED and must NOT arm the stall watchdog: no audio
        // is meant to flow until the user hits play, so the silence here is expected, not a fault.
        // Arming it let the watchdog "recover" a deliberately-paused restore by skipping to — and
        // starting — the next track. The generator keeps producing in the background to pre-fill the
        // resume buffer; the first play() consumes pendingResumeMs as a seek. Landing PAUSED here
        // (rather than waiting for the first Emitting) also means the restore doesn't depend on the
        // generator producing a buffer, which it can't when a surviving-process buffer pool is stale.
        if (m.pendingResumeMs != null) {
            hatchet.i(
                "handleGeneratorLoading(track=${event.trackId}): " +
                    "${m.playback.state} -> PAUSED (restored session, awaiting play)."
            )
            return armed.copy(
                playback = m.playback.copy(
                    state = PlayerState.PAUSED,
                    generatorProducedMs = 0L,
                    cachedMs = 0L,
                    skipForwardAllowed = skipForwardAllowed,
                ),
            ).with(Effect.EmitMetadata(newTrack))
        }

        hatchet.i(
            "handleGeneratorLoading(track=${event.trackId}): " +
                "${m.playback.state} -> BUFFERING (metadata emitted)."
        )
        return armed.copy(
            playback = m.playback.copy(
                state = PlayerState.BUFFERING,
                generatorProducedMs = 0L,
                cachedMs = 0L,
                skipForwardAllowed = skipForwardAllowed,
            ),
        ).with(Effect.ArmWatchdog, Effect.EmitMetadata(newTrack))
    }

    private fun reduceGeneratorEmitting(
        m: Model,
        event: GeneratorEvent.Emitting,
    ): Pair<Model, List<Effect>> {
        // Ignore a straggler buffer from a track we've already skipped past: applying it would
        // rewind generatorProducedMs and clear the failure streak against audio the user is no
        // longer hearing. currentTrackId is null only before a setlist exists, where there's
        // nothing to skip past, so fall through.
        val currentTrackId = currentTrackId(m)
        if (currentTrackId != null && event.trackId != currentTrackId) {
            return m.with()
        }

        // Fallback for a restore that's somehow still BUFFERING when its first buffer arrives
        // (reduceGeneratorLoading now lands a restore in PAUSED up front). Land in PAUSED without
        // starting the speaker, so launch stays silent. pendingResumeMs is intentionally kept — the
        // first play() consumes it as a seek to the saved offset. Only this first buffer matters;
        // once PAUSED, later Emittings fall through to the normal watermark update below (their
        // ArmWatchdog no-ops while paused).
        if (m.playback.state == PlayerState.BUFFERING && m.pendingResumeMs != null) {
            return m.copy(
                consecutiveFailures = 0,
                lastRenderProgressMs = maxOf(m.lastRenderProgressMs, event.cachedMs),
                playback = m.playback.copy(
                    state = PlayerState.PAUSED,
                    generatorProducedMs = event.producedMs,
                    cachedMs = event.cachedMs,
                ),
            ).with(Effect.CancelWatchdog)
        }

        // The current track is producing audio — the failure streak is broken and the stall guard
        // resets (playback progressed). Track the watermark so a later render wait is measured
        // against the furthest point already rendered. If we were buffering, kick the speaker.
        val effects = buildList {
            if (m.playback.state == PlayerState.BUFFERING) add(Effect.SpeakerPlay)
            add(Effect.ArmWatchdog)
        }
        return m.copy(
            consecutiveFailures = 0,
            lastRenderProgressMs = maxOf(m.lastRenderProgressMs, event.cachedMs),
            playback = m.playback.copy(
                generatorProducedMs = event.producedMs,
                cachedMs = event.cachedMs,
            ),
        ) to effects
    }

    /**
     * The generator's current track ended. The session's [Session.repeatMode] decides what comes
     * next:
     *  - [RepeatMode.ONE] re-queues the same track (position unchanged), looping it indefinitely.
     *  - otherwise, with a track still to come, advance the setlist position and start it (the
     *    speaker rides into the next track's buffers on its own — no forced switch).
     *  - at the end of the setlist, [RepeatMode.ALL] wraps back to position 0; [RepeatMode.OFF]
     *    transitions to [PlayerState.ENDING] and stops the generator while the speaker plays out
     *    what's buffered ([reduceSpeakerBuffering] completes the session once it drains).
     */
    private fun reduceTrackChange(m: Model): Pair<Model, List<Effect>> {
        val session = m.session
        val setlist = m.setlist
        if (session == null || setlist == null) {
            val detail = if (session == null) "Invalid session." else "Invalid setlist."
            hatchet.e("Error: $detail")
            return m.copy(
                playback = m.playback.copy(state = PlayerState.ERROR, errorMessage = detail),
            ).with(Effect.PublishError(detail, currentTrackId(m)))
        }

        // Repeat-one: restart the current track in place, leaving the setlist position untouched.
        if (session.repeatMode == RepeatMode.ONE) {
            val trackId = currentTrackId(m)
            if (trackId != null) {
                hatchet.d("Track ended; repeat-one is on — restarting the current track.")
                return m.with(Effect.StartTrack(trackId))
            }
            // No resolvable current track to repeat — fall through to normal advance/end handling.
        }

        return if (isLast(session, setlist)) {
            if (wrapsAtEnd(session, setlist)) {
                hatchet.d("End of setlist; repeat-all is on — wrapping back to the first track.")
                m.copy(session = session.copy(currentPosition = 0))
                    .with(Effect.StartTrack(setlist[0]))
            } else {
                hatchet.d("Generator requested next track, but no more exist.")
                m.copy(playback = m.playback.copy(state = PlayerState.ENDING))
                    .with(Effect.CancelWatchdog, Effect.StopGenerator)
            }
        } else {
            val nextPosition = (session.currentPosition ?: -1) + 1
            m.copy(session = session.copy(currentPosition = nextPosition))
                .with(Effect.StartTrack(setlist[nextPosition]))
        }
    }

    /**
     * A generator error is treated as a bad track, not a fatal session error: log it and skip to
     * the next track in the setlist (dropping the failed track's queued audio and switching the
     * speaker over promptly). The generator is stopped before the relaunch so its self-terminating
     * loop can't race startTrack into an "Already looping" no-op. The session is only stopped when
     * there's nothing left to try: no setlist to recover within, the failed track was the last one,
     * or [MAX_CONSECUTIVE_FAILURES] tracks failed in a row with no audio in between (the streak
     * resets in [reduceGeneratorEmitting]).
     */
    private fun reduceGeneratorError(
        m: Model,
        event: GeneratorEvent.Error,
    ): Pair<Model, List<Effect>> {
        val session = m.session
        val setlist = m.setlist
        val failedTrackId = currentTrackId(m)

        if (session == null || setlist == null) {
            hatchet.e("Error: ${event.message}")
            return m.copy(
                playback = m.playback.copy(state = PlayerState.ERROR, errorMessage = event.message),
            ).with(
                Effect.CancelWatchdog,
                Effect.PublishError(event.message, failedTrackId),
                Effect.StopSpeaker,
                Effect.StopGenerator,
            )
        }

        val failures = m.consecutiveFailures + 1
        val withFailure = m.copy(consecutiveFailures = failures)
        return when {
            failures >= MAX_CONSECUTIVE_FAILURES -> {
                val message = "Playback stopped after $MAX_CONSECUTIVE_FAILURES " +
                    "consecutive track failures. Last error: ${event.message}"
                hatchet.e("Error: $message")
                withFailure.copy(
                    playback = m.playback.copy(state = PlayerState.ERROR, errorMessage = message),
                ).with(
                    Effect.CancelWatchdog,
                    Effect.PublishError(message, failedTrackId),
                    Effect.StopSpeaker,
                    Effect.StopGenerator,
                )
            }

            isLast(session, setlist) -> {
                hatchet.e("Generator error on last track: ${event.message}. Ending session.")
                withFailure.copy(playback = m.playback.copy(state = PlayerState.STOPPED)).with(
                    Effect.CancelWatchdog,
                    Effect.PublishError(event.message, failedTrackId),
                    Effect.StopSpeaker,
                    Effect.StopGenerator,
                )
            }

            else -> {
                hatchet.e(
                    "Generator error ($failures/$MAX_CONSECUTIVE_FAILURES): " +
                        "${event.message}. Skipping to the next track."
                )
                val nextPosition = (session.currentPosition ?: -1) + 1
                val nextTrackId = setlist[nextPosition]
                withFailure.copy(session = session.copy(currentPosition = nextPosition)).with(
                    Effect.CancelWatchdog,
                    Effect.PublishError(event.message, failedTrackId),
                    Effect.StopGenerator,
                    Effect.StartTrack(nextTrackId),
                    Effect.SwitchSpeaker(nextTrackId),
                )
            }
        }
    }

    // ---- speaker-event reducers ----

    internal suspend fun reduce(m: Model, event: SpeakerEvent): Pair<Model, List<Effect>> = when (event) {
        is SpeakerEvent.Buffering -> reduceSpeakerBuffering(m)
        is SpeakerEvent.Playing -> reduceSpeakerPlaying(m)
        is SpeakerEvent.TrackChange -> reduceSpeakerTrackChange(m, event.trackId)
        is SpeakerEvent.Error -> reduceSpeakerError(m, event)
    }

    private fun reduceSpeakerBuffering(m: Model): Pair<Model, List<Effect>> = when (m.playback.state) {
        PlayerState.ENDING -> {
            // The tail of the final track has drained — the setlist is complete.
            hatchet.i("Setlist complete.")
            m.copy(playback = m.playback.copy(state = PlayerState.STOPPED))
                .with(Effect.CancelWatchdog, Effect.StopSpeaker, Effect.StopGenerator)
        }

        // Speaker ran dry — a mid-track underrun or the gap while a skipped-to track loads.
        // Either way audio has stopped, so surface it; recovers on the next SpeakerEvent.Playing.
        PlayerState.PLAYING -> {
            hatchet.w("Speaker starved -> BUFFERING.")
            m.copy(playback = m.playback.copy(state = PlayerState.BUFFERING)).with()
        }

        else -> m.with()
    }

    private fun reduceSpeakerPlaying(m: Model): Pair<Model, List<Effect>> = when (m.playback.state) {
        // Audio is flowing again. Only BUFFERING needs flipping; ENDING rides out its tail, and
        // paused/stopped/idle/error have no consume loop so a Playing event there would be a
        // stray we deliberately ignore rather than resurrecting playback.
        PlayerState.BUFFERING -> {
            hatchet.i("Buffering resolved -> PLAYING.")
            m.copy(playback = m.playback.copy(state = PlayerState.PLAYING)).with()
        }

        else -> m.with()
    }

    private suspend fun reduceSpeakerTrackChange(m: Model, newTrackId: Long): Pair<Model, List<Effect>> {
        val newTrack = getTrack(newTrackId) ?: return metadataLoadError(m, newTrackId)
        hatchet.i("updatePlayerMetadata(track=$newTrackId, ${newTrack.title}): emitting metadata.")
        // State follows the speaker's Playing/Buffering flow, not metadata: a TrackChange is
        // always immediately followed by a Playing event that flips BUFFERING -> PLAYING.
        return m.with(Effect.EmitMetadata(newTrack))
    }

    private fun reduceSpeakerError(m: Model, event: SpeakerEvent.Error): Pair<Model, List<Effect>> {
        hatchet.e("Error: ${event.message}")
        return m.copy(
            playback = m.playback.copy(state = PlayerState.ERROR, errorMessage = event.message),
        ).with(
            Effect.CancelWatchdog,
            Effect.PublishError(event.message, currentTrackId(m)),
            Effect.StopSpeaker,
            Effect.StopGenerator,
        )
    }

    private suspend fun getTrack(id: Long) = repository.getTrack(id, withArtists = true, withGame = true)

    /**
     * Publish a non-fatal error to [errorEventsMutable], attaching the [Track] for [trackId] so
     * consumers can attribute the error to the right track even when the speaker hasn't caught up
     * yet. The repository lookup is best-effort: if it fails we still publish the message with a
     * null track rather than dropping the event.
     */
    private suspend fun publishError(message: String, trackId: Long?) {
        val track = trackId?.let { runCatching { getTrack(it) }.getOrNull() }
        errorEventsMutable.tryEmit(PlayerErrorEvent(message, track))
    }

    /** Imperative-path error: publish [message], log it, and move the live model to
     *  [PlayerState.ERROR]. Used by transport methods (which aren't reducers). */
    private suspend fun emitError(message: String, trackId: Long? = currentTrackId(model)) {
        hatchet.e("Error: $message")
        publishError(message, trackId)
        commit(model.copy(playback = model.playback.copy(state = PlayerState.ERROR, errorMessage = message)))
    }

    /** Reduce a failed track-metadata fetch to an ERROR state, logging the message to the error
     *  stream. [trackId] is the id that failed to resolve; we still pass it to the [Effect.PublishError]
     *  attribution, which will attempt (and likely also fail) to load it — yielding a null track in
     *  the event, which the UI treats as "no track prefix". */
    private fun metadataLoadError(m: Model, trackId: Long?): Pair<Model, List<Effect>> {
        val message = "Couldn't load track metadata."
        return m.copy(
            playback = m.playback.copy(state = PlayerState.ERROR, errorMessage = message),
        ).with(Effect.CancelWatchdog, Effect.PublishError(message, trackId))
    }
}
