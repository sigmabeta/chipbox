package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerErrorEvent
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.GeneratorEvent
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.player.speaker.SpeakerEvent
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet

private const val SKIP_BACK_THRESHOLD_MS = 3_000L

/** Consecutive generator errors (with no successful audio in between) before the director
 *  gives up and stops the session instead of skipping to yet another track. */
private const val MAX_CONSECUTIVE_FAILURES = 3

/**
 * Production [Director] implementation.
 *
 * On construction, two coroutines are launched on [directorScope] that subscribe to
 * [Generator.events] and [Speaker.events] for the lifetime of this object. Each event is fed
 * through a `reduce(state, event)` function (one overload per event type) that returns the next
 * [ChipboxPlaybackState]. Assigning to [currentState] re-emits the new value to observers via
 * the property's setter.
 *
 * Setlist resolution is driven by [Session.type]: `GAME`, `ARTIST`, and `ALL_TRACKS` sessions
 * pull tracks for the given scope from the repository; `PLAYLIST` is not yet implemented. The
 * director
 * also decides when to advance tracks — the generator emits [GeneratorEvent.TrackChange] when
 * its current track ends, and the director responds by feeding it the next track id from the
 * setlist (or transitioning to [PlayerState.ENDING] if the setlist is exhausted).
 */
class RealDirector(
    private val generator: Generator,
    private val speaker: Speaker,
    private val repository: Repository,
    private val hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Director {
    private val directorScope = CoroutineScope(SupervisorJob() + dispatcher)

    fun release() {
        directorScope.cancel()
    }

    private var currentSession: Session? = null
        set(value) {
            field = value
            directorScope.launch {
                sessionStateMutable.emit(value)
            }
        }

    private var currentSetlist: List<Long>? = null

    /** Number of generator errors since the last successful audio emission. Reset whenever a
     *  track actually produces audio (or a fresh session starts); drives the give-up cutoff. */
    private var consecutiveGeneratorFailures: Int = 0

    private var currentState: ChipboxPlaybackState = ChipboxPlaybackState(
        state = PlayerState.IDLE,
        position = 0L,
        generatorProducedMs = 0L,
        playbackSpeed = 1.0f,
        skipForwardAllowed = false,
        errorMessage = null,
    )
        set(value) {
            // Stamp the speaker's current position on every emission so pause/resume and
            // track-change reports anchor the notification's progress bar to actual played
            // audio. During PLAYING the Android framework extrapolates forward from the last
            // anchor, so this only needs to be right at state-transition moments.
            field = value.copy(position = speaker.currentPositionMs())
            directorScope.launch {
                playbackStateMutable.emit(currentState)
            }
        }

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

    init {
        // Seed each replay buffer so a subscriber that attaches before any playback has
        // happened gets a meaningful "nothing playing" emission instead of hanging on an
        // empty flow.
        metadataStateMutable.tryEmit(null)
        playbackStateMutable.tryEmit(currentState)
        sessionStateMutable.tryEmit(null)

        directorScope.launch {
            generator
                .events()
                .distinctUntilChanged()
                .collect {
                    currentState = reduce(currentState, it)
                }
        }

        directorScope.launch {
            speaker
                .events()
                .distinctUntilChanged()
                .collect {
                    currentState = reduce(currentState, it)
                }
        }
    }

    override fun start(session: Session) {
        directorScope.launch {
            consecutiveGeneratorFailures = 0
            val setlistForSession = getSetlistForSession(session)
                .let { if (session.shuffled) it.shuffled() else it }

            currentSession = session
            currentSetlist = setlistForSession

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

                val wasPaused = currentState.state == PlayerState.PAUSED
                currentSession = session.copy(
                    currentPosition = startingPosition
                )
                startTrack(firstTrackId)
                if (wasPaused) {
                    // Drop the audio queued from the paused session and restart the speaker's
                    // consume loop, switching it onto the new track so any buffer left from the
                    // paused session's track is discarded rather than played. Without this, the
                    // generator stays blocked filling buffers nobody is reading, so the new
                    // session never becomes audible. Skipped on cold start because the buffer
                    // manager isn't initialised until the generator's first setSampleRate lands.
                    speaker.switchTo(firstTrackId)
                }
            }
        }
    }

    override fun start(
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

    override fun play() {
        if (currentState.state == PlayerState.PAUSED) {
            speaker.play()
            currentState = currentState.copy(state = PlayerState.PLAYING)
        }
        generator.play()
    }

    override fun pause() {
        directorScope.launch {
            speaker.pause()

            currentState = currentState.copy(state = PlayerState.PAUSED)
        }
    }

    override fun stop() {
        directorScope.launch {
            speaker.stop()
            generator.stop()

            currentState = currentState.copy(state = PlayerState.STOPPED)
        }
    }

    override fun seek(positionMs: Long) {
        directorScope.launch {
            generator.seek(positionMs)
            speaker.seek()
        }
    }

    override fun skipForward() {
        directorScope.launch {
            val session = currentSession ?: return@launch
            val setlist = currentSetlist ?: return@launch
            if (isCurrentTrackLastInSetlist(session, setlist)) return@launch

            val nextPosition = (session.currentPosition ?: -1) + 1
            val nextTrackId = setlist[nextPosition]
            hatchet.i("skipForward: advancing to position $nextPosition (state=${currentState.state}).")
            advanceToTrackAt(session, setlist, nextPosition)
            // Cut over to the new track immediately: drop the play-out buffer and have the
            // speaker discard any straggler from the outgoing track until the new one's audio
            // arrives. The auto-advance path naturally reaches end-of-buffer so doesn't need this.
            hatchet.i("skipForward: generator.startTrack returned; calling speaker.switchTo($nextTrackId).")
            speaker.switchTo(nextTrackId)
            hatchet.i("skipForward: speaker.switchTo returned (state=${currentState.state}).")
        }
    }

    override fun skipBack() {
        directorScope.launch {
            val session = currentSession ?: return@launch
            val setlist = currentSetlist ?: return@launch
            val withinTrackPosition = currentState.position
            val setlistPosition = session.currentPosition ?: 0

            if (withinTrackPosition > SKIP_BACK_THRESHOLD_MS || setlistPosition <= 0) {
                // Restart of the current track — no track change, so a plain in-track seek.
                generator.seek(0L)
                speaker.seek()
                return@launch
            }

            val previousTrackId = setlist[setlistPosition - 1]
            advanceToTrackAt(session, setlist, setlistPosition - 1)
            speaker.switchTo(previousTrackId)
        }
    }

    override fun setShuffled(shuffled: Boolean) {
        directorScope.launch {
            val session = currentSession ?: return@launch
            if (session.shuffled == shuffled) return@launch

            // Remember which track is playing so we can find it in the new ordering.
            val playingTrackId = currentSetlist
                ?.let { setlist -> session.currentPosition?.let(setlist::getOrNull) }

            val newSetlist = getSetlistForSession(session)
                .let { if (shuffled) it.shuffled() else it }
            val newPosition = playingTrackId
                ?.let { id -> newSetlist.indexOf(id).takeIf { it >= 0 } }
                ?: 0

            currentSetlist = newSetlist
            currentSession = session.copy(
                shuffled = shuffled,
                currentPosition = newPosition,
            )
        }
    }

    override fun metadataState() = metadataStateMutable.asSharedFlow()

    override fun playbackState() = playbackStateMutable.asSharedFlow()

    override fun sessionState() = sessionStateMutable.asSharedFlow()

    override fun errorEvents() = errorEventsMutable.asSharedFlow()

    override fun pauseTemporarily() {
        directorScope.launch {
            speaker.pause()
        }
    }

    /**
     * 🦆 Drop the speaker's output to 50% but keep playing — the OS only asked us to get out
     * of the way of a transient sound, not to stop.
     */
    override fun duck() {
        speaker.setDucked(true)
    }

    override fun resumeFocus() {
        // Undo a duck() (no-op if we weren't ducked) and restart the consume loop if a
        // pauseTemporarily() had stopped it (no-op if it's already running).
        speaker.setDucked(false)
        speaker.play()
    }

    override fun setVolume(scale: Double) {
        speaker.setVolume(scale)
    }

    private suspend fun startTrack(trackId: Long) {
        generator.startTrack(trackId)
    }

    private fun nextTrack() {
        directorScope.launch {
            val session = currentSession
            val setlist = currentSetlist

            if (session == null) {
                emitError("Invalid session.")
                return@launch
            }

            if (setlist == null) {
                emitError("Invalid setlist.")
                return@launch
            }

            if (isCurrentTrackLastInSetlist(session, setlist)) {
                // TODO This should also have a reducer.
                hatchet.d("Generator requested next track, but no more exist.")
                currentState = currentState.copy(state = PlayerState.ENDING)
                generator.stop()
                return@launch
            }

            val nextTrackPosition = (session.currentPosition ?: -1) + 1
            advanceToTrackAt(session, setlist, nextTrackPosition)
        }
    }

    private suspend fun advanceToTrackAt(
        session: Session,
        setlist: List<Long>,
        newPosition: Int,
    ) {
        currentSession = session.copy(currentPosition = newPosition)
        startTrack(setlist[newPosition])
    }

    private fun isCurrentTrackLastInSetlist(session: Session, setlist: List<Long>): Boolean {
        val nextTrackPosition = (session.currentPosition ?: -1) + 1
        return nextTrackPosition >= setlist.size
    }

    private suspend fun getSetlistForSession(session: Session) = when (session.type) {
        SessionType.GAME -> getTrackListForGame(session.contentId)
        SessionType.ARTIST -> getTrackListForArtist(session.contentId)
        SessionType.PLAYLIST -> getTrackListForPlaylist(session.contentId)
        SessionType.ALL_TRACKS -> getTrackListForAllTracks()
        SessionType.PLATFORM -> getTrackListForPlatform(session.contentId)
        SessionType.SETLIST -> session.explicitSetlist.orEmpty()
        SessionType.SINGLE_TRACK -> listOf(session.contentId)
    }

    private suspend fun getTrackListForPlatform(contentId: Long) = repository
        .getTracksForPlatform(Platform.entries[contentId.toInt()])
        .map { it.id }

    private suspend fun getTrackListForGame(gameId: Long) = repository
        .getTracksForGame(gameId)
        .map { it.id }

    private suspend fun getTrackListForArtist(artistId: Long) = repository
        .getTracksForArtist(artistId)
        .map { it.id }

    private fun getTrackListForPlaylist(playlistId: Long): List<Long> {
        TODO("Not yet implemented")
    }

    private suspend fun getTrackListForAllTracks(): List<Long> = repository
        .getAllTracks(withGame = false, withArtists = false)
        .filter { it is Data.Succeeded }
        .map { (it as Data.Succeeded).data }
        .first()
        .map { it.id }

    private suspend fun reduce(oldState: ChipboxPlaybackState, event: GeneratorEvent) = when (event) {
        is GeneratorEvent.Error -> handleGeneratorError(event, oldState)
        is GeneratorEvent.Loading -> handleGeneratorLoading(oldState, event)
        is GeneratorEvent.Emitting -> handleGeneratorEmitting(oldState, event)
        GeneratorEvent.TrackChange -> handleGeneratorTrackChange(oldState)
    }

    private suspend fun handleGeneratorLoading(
        oldState: ChipboxPlaybackState,
        event: GeneratorEvent.Loading,
    ): ChipboxPlaybackState {
        val session = currentSession
        val setlist = currentSetlist

        if (session == null) {
            emitError("Invalid session.")
            return oldState.copy(
                state = PlayerState.ERROR,
                errorMessage = "Unable to determine if next track available."
            )
        }

        if (setlist == null) {
            emitError("Invalid setlist.")
            return oldState.copy(
                state = PlayerState.ERROR,
                errorMessage = "Unable to determine if next track available."
            )
        }

        // Already mid-playback (audio flowing) or mid-buffer (starved): a track change is in
        // flight. Don't force a state — the speaker decides PLAYING vs BUFFERING by whether audio
        // keeps flowing, and the now-playing metadata updates when the new track's first buffer
        // plays (SpeakerEvent.TrackChange). Just reset the high-water mark, cache progress, and
        // skip-forward gate.
        if (oldState.state == PlayerState.PLAYING || oldState.state == PlayerState.BUFFERING) {
            hatchet.i(
                "handleGeneratorLoading(track=${event.trackId}): " +
                    "track change while ${oldState.state}; awaiting audio."
            )
            return oldState.copy(
                generatorProducedMs = 0L,
                cachedMs = 0L,
                skipForwardAllowed = !isCurrentTrackLastInSetlist(session, setlist),
            )
        }

        // Nothing playing yet (cold start / resumed from a stopped-ish state): show the spinner
        // with this track's metadata and wait for the first buffer.
        val newTrack = getTrack(event.trackId) ?: return metadataLoadError(oldState, event.trackId)
        metadataStateMutable.emit(newTrack)
        hatchet.i(
            "handleGeneratorLoading(track=${event.trackId}): " +
                "${oldState.state} -> BUFFERING (metadata emitted)."
        )

        return oldState.copy(
            state = PlayerState.BUFFERING,
            generatorProducedMs = 0L,
            cachedMs = 0L,
            skipForwardAllowed = !isCurrentTrackLastInSetlist(session, setlist),
        )
    }

    private fun handleGeneratorEmitting(
        oldState: ChipboxPlaybackState,
        event: GeneratorEvent.Emitting,
    ): ChipboxPlaybackState {
        // Ignore a straggler buffer from a track we've already skipped past: applying it would
        // rewind generatorProducedMs and clear the failure streak against audio the user is no
        // longer hearing. currentTrackId() is null only before a setlist exists, where there's
        // nothing to skip past, so fall through.
        val currentTrackId = currentTrackId()
        if (currentTrackId != null && event.trackId != currentTrackId) {
            return oldState
        }

        if (oldState.state == PlayerState.BUFFERING) {
            speaker.play()
        }

        // The current track is producing audio — the failure streak is broken.
        consecutiveGeneratorFailures = 0

        return oldState.copy(
            generatorProducedMs = event.producedMs,
            cachedMs = event.cachedMs,
        )
    }

    /** Track id the director currently considers active, from the live setlist position.
     *  Null until a session + setlist are established. */
    private fun currentTrackId(): Long? {
        val position = currentSession?.currentPosition ?: return null
        return currentSetlist?.getOrNull(position)
    }

    private fun handleGeneratorTrackChange(oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        nextTrack()
        return oldState
    }

    /**
     * A generator error is treated as a bad track, not a fatal session error: log it and skip
     * to the next track in the setlist (mirroring the user-initiated [skipForward] path so the
     * failed track's queued audio is dropped and playback switches promptly). The session is
     * only stopped when there's nothing left to try: no setlist to recover within, the failed
     * track was the last one, or [MAX_CONSECUTIVE_FAILURES] tracks have failed in a row with
     * no audio in between (the streak resets in [handleGeneratorEmitting]).
     */
    private suspend fun handleGeneratorError(
        event: GeneratorEvent.Error,
        oldState: ChipboxPlaybackState,
    ): ChipboxPlaybackState {
        val session = currentSession
        val setlist = currentSetlist

        return when {
            session == null || setlist == null -> {
                emitError(event.message)
                directorScope.launch {
                    speaker.stop()
                    generator.stop()
                }
                oldState.copy(state = PlayerState.ERROR, errorMessage = event.message)
            }

            else -> {
                consecutiveGeneratorFailures += 1
                when {
                    consecutiveGeneratorFailures >= MAX_CONSECUTIVE_FAILURES -> {
                        val message = "Playback stopped after $MAX_CONSECUTIVE_FAILURES " +
                            "consecutive track failures. Last error: ${event.message}"
                        emitError(message)
                        directorScope.launch {
                            speaker.stop()
                            generator.stop()
                        }
                        oldState.copy(state = PlayerState.ERROR, errorMessage = message)
                    }

                    isCurrentTrackLastInSetlist(session, setlist) -> {
                        hatchet.w(
                            "Generator error on last track: ${event.message}. Ending session."
                        )
                        publishError(event.message)
                        directorScope.launch {
                            speaker.stop()
                            generator.stop()
                        }
                        oldState.copy(state = PlayerState.STOPPED)
                    }

                    else -> {
                        hatchet.w(
                            "Generator error " +
                                "($consecutiveGeneratorFailures/$MAX_CONSECUTIVE_FAILURES): " +
                                "${event.message}. Skipping to the next track."
                        )
                        publishError(event.message)
                        val nextPosition = (session.currentPosition ?: -1) + 1
                        val nextTrackId = setlist[nextPosition]
                        directorScope.launch {
                            advanceToTrackAt(session, setlist, nextPosition)
                            speaker.switchTo(nextTrackId)
                        }
                        oldState
                    }
                }
            }
        }
    }

    private suspend fun reduce(oldState: ChipboxPlaybackState, event: SpeakerEvent) = when (event) {
        is SpeakerEvent.Buffering -> handleSpeakerBuffering(oldState)
        is SpeakerEvent.Playing -> handleSpeakerPlaying(oldState)
        is SpeakerEvent.TrackChange -> updatePlayerMetadata(oldState, event.trackId)
        is SpeakerEvent.Error -> handleSpeakerError(event, oldState)
    }

    private fun handleSpeakerBuffering(oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        if (oldState.state == PlayerState.ENDING) {
            hatchet.i("Setlist complete.")
            stop()
            return oldState.copy(state = PlayerState.STOPPED)
        }

        // Speaker ran dry — a mid-track underrun or the gap while a skipped-to track loads.
        // Either way audio has stopped, so surface it; recovers on the next SpeakerEvent.Playing.
        if (oldState.state == PlayerState.PLAYING) {
            hatchet.w("Speaker starved -> BUFFERING.")
            return oldState.copy(state = PlayerState.BUFFERING)
        }

        return oldState
    }

    private fun handleSpeakerPlaying(oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        // Audio is flowing again. Only BUFFERING needs flipping; ENDING rides out its tail, and
        // paused/stopped/idle/error have no consume loop so a Playing event there would be a
        // stray we deliberately ignore rather than resurrecting playback.
        return when (oldState.state) {
            PlayerState.BUFFERING -> {
                hatchet.i("Buffering resolved -> PLAYING.")
                oldState.copy(state = PlayerState.PLAYING)
            }

            else -> oldState
        }
    }

    private suspend fun updatePlayerMetadata(oldState: ChipboxPlaybackState, newTrackId: Long): ChipboxPlaybackState {
        val newTrack = getTrack(newTrackId) ?: return metadataLoadError(oldState, newTrackId)
        hatchet.i(
            "updatePlayerMetadata(track=$newTrackId, ${newTrack.title}): emitting metadata."
        )
        metadataStateMutable.emit(newTrack)
        // State follows the speaker's Playing/Buffering flow, not metadata: a TrackChange is
        // always immediately followed by a Playing event that flips BUFFERING -> PLAYING.
        return oldState
    }

    private suspend fun handleSpeakerError(
        event: SpeakerEvent.Error,
        oldState: ChipboxPlaybackState,
    ): ChipboxPlaybackState {
        emitError(event.message)

        directorScope.launch {
            speaker.stop()
            generator.stop()
        }

        return oldState.copy(state = PlayerState.ERROR, errorMessage = event.message)
    }

    private suspend fun getTrack(id: Long) = repository.getTrack(id, withArtists = true, withGame = true)

    /**
     * Publish a non-fatal error to [errorEventsMutable], attaching the [Track] currently
     * associated with [trackId] (defaulting to the director's active track id) so consumers can
     * attribute the error to the right track even when the speaker hasn't caught up yet. The
     * repository lookup is best-effort: if it fails we still publish the message with a null
     * track rather than dropping the event.
     */
    private suspend fun publishError(message: String, trackId: Long? = currentTrackId()) {
        val track = trackId?.let { runCatching { getTrack(it) }.getOrNull() }
        errorEventsMutable.tryEmit(PlayerErrorEvent(message, track))
    }

    /** Publish [message] and transition the session to [PlayerState.ERROR]. Track attribution
     *  follows [publishError]. */
    private suspend fun emitError(message: String, trackId: Long? = currentTrackId()) {
        hatchet.e("Error: $message")
        publishError(message, trackId)
        currentState = currentState.copy(
            state = PlayerState.ERROR,
            errorMessage = message,
        )
    }

    /** Reduce a failed track-metadata fetch to an ERROR state, logging the message to the error
     *  stream. Unlike [emitError] this returns the new state for the reducer to assign rather than
     *  mutating [currentState] directly. [trackId] is the id that failed to resolve; we still pass
     *  it to [publishError], which will attempt (and likely also fail) to load it — yielding a
     *  null track in the event, which the UI treats as "no track prefix". */
    private suspend fun metadataLoadError(
        oldState: ChipboxPlaybackState,
        trackId: Long?,
    ): ChipboxPlaybackState {
        val message = "Couldn't load track metadata."
        publishError(message, trackId)
        return oldState.copy(state = PlayerState.ERROR, errorMessage = message)
    }
}
