package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.GeneratorEvent
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.player.speaker.SpeakerEvent
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production [Director] implementation.
 *
 * On construction, two coroutines are launched on [directorScope] that subscribe to
 * [Generator.events] and [Speaker.events] for the lifetime of this object. Each event is fed
 * through a `reduce(state, event)` function (one overload per event type) that returns the next
 * [ChipboxPlaybackState]. Assigning to [currentState] re-emits the new value to observers via
 * the property's setter.
 *
 * Setlist resolution is driven by [Session.type]: a `GAME` session pulls every track for the
 * given game from the repository; an `ARTIST` session is not yet implemented. The director
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
                    hatchet.d("Received GeneratorEvent: $it")
                    currentState = reduce(currentState, it)
                }
        }

        directorScope.launch {
            speaker
                .events()
                .distinctUntilChanged()
                .collect {
                    hatchet.d("Received SpeakerEvent: $it")
                    currentState = reduce(currentState, it)
                }
        }
    }

    override fun start(session: Session) {
        directorScope.launch {
            val setlistForSession = getSetlistForSession(session)
                .let { if (session.shuffled) it.shuffled() else it }

            currentSession = session
            currentSetlist = setlistForSession

            val firstTrackId = when {
                session.startingTrackId != null -> session.startingTrackId
                session.currentPosition != null -> setlistForSession[session.currentPosition!!]
                session.startingPosition != null -> setlistForSession[session.startingPosition!!]
                else -> {
                    emitError("Unable to find a track id to play.")
                    return@launch
                }
            }

            if (firstTrackId != null) {
                val startingPosition = session.startingPosition
                    ?: setlistForSession.indexOfFirst { it == firstTrackId }

                currentSession = session.copy(
                    currentPosition = startingPosition
                )
                startTrack(firstTrackId)
            }
        }
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

    override fun pauseTemporarily() {
        directorScope.launch {
            speaker.pause()
        }
    }

    /**
     * 🦆
     */
    override fun duck() {
        pauseTemporarily()
    }

    override fun resumeFocus() {
        // 🚫🦆
        // speaker.unduck()
        speaker.play()
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

            currentSession = session.copy(currentPosition = nextTrackPosition)
            val nextTrack = setlist[nextTrackPosition]

            startTrack(nextTrack)
        }
    }

    private fun isCurrentTrackLastInSetlist(session: Session, setlist: List<Long>): Boolean {
        val nextTrackPosition = (session.currentPosition ?: -1) + 1
        return nextTrackPosition >= setlist.size
    }

    private fun getSetlistForSession(session: Session) = when (session.type) {
        SessionType.GAME -> getTrackListForGame(session.contentId)
        SessionType.ARTIST -> getTrackListForArtist(session.contentId)
    }

    private fun getTrackListForGame(gameId: Long) = repository
        .getTracksForGame(gameId)
        .map { it.id }

    private fun getTrackListForArtist(artistId: Long): List<Long> {
        TODO("Not yet implemented")
    }

    private suspend fun reduce(oldState: ChipboxPlaybackState, event: GeneratorEvent) = when (event) {
        is GeneratorEvent.Error -> handleGeneratorError(event, oldState)
        is GeneratorEvent.Loading -> handleGeneratorLoading(oldState, event)
        is GeneratorEvent.Emitting -> handleGeneratorEmitting(oldState, event)
        GeneratorEvent.TrackChange -> handleGeneratorTrackChange(oldState)
    }

    private suspend fun handleGeneratorLoading(oldState: ChipboxPlaybackState, event: GeneratorEvent.Loading): ChipboxPlaybackState {
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

        if (oldState.state == PlayerState.PLAYING) {
            return oldState.copy(
                state = PlayerState.PRELOADING,
                generatorProducedMs = 0L,
                skipForwardAllowed = isCurrentTrackLastInSetlist(session, setlist)
            )

        }

        val newTrack = getTrack(event.trackId) ?: return oldState.copy(state = PlayerState.ERROR)
        metadataStateMutable.emit(newTrack)

        return oldState.copy(
            state = PlayerState.BUFFERING,
            generatorProducedMs = 0L,
            skipForwardAllowed = isCurrentTrackLastInSetlist(session, setlist)
        )
    }

    private fun handleGeneratorEmitting(oldState: ChipboxPlaybackState, event: GeneratorEvent.Emitting): ChipboxPlaybackState {
        if (oldState.state == PlayerState.BUFFERING) {
            speaker.play()
        }

        return oldState.copy(generatorProducedMs = event.producedMs)
    }

    private fun handleGeneratorTrackChange(oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        nextTrack()
        return oldState
    }

    private fun handleGeneratorError(event: GeneratorEvent.Error, oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        emitError(event.message)

        directorScope.launch {
            speaker.stop()
            generator.stop()
        }

        return oldState.copy(state = PlayerState.ERROR, errorMessage = event.message)
    }

    private suspend fun reduce(oldState: ChipboxPlaybackState, event: SpeakerEvent) = when (event) {
        is SpeakerEvent.Buffering -> handleSpeakerBuffering(oldState)
        is SpeakerEvent.Playing -> handleSpeakerPlaying(oldState)
        is SpeakerEvent.TrackChange -> updatePlayerMetadata(oldState, event.trackId)
        is SpeakerEvent.Error -> handleSpeakerError(event, oldState)
    }

    private fun handleSpeakerBuffering(oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        if (oldState.state == PlayerState.PLAYING) {
            hatchet.w("Buffer underrun.")
            return oldState
        }

        if (oldState.state == PlayerState.ENDING) {
            hatchet.i("Setlist complete.")
            stop()
            return oldState.copy(state = PlayerState.STOPPED)
        }

//        emitError("SpeakerEvent.BUFFERING not expected in state $oldState.")
        return oldState
    }

    private fun handleSpeakerPlaying(oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        if (oldState.state == PlayerState.BUFFERING) {
            hatchet.i("Underrun resolved.")
        }

        if (oldState.state == PlayerState.ENDING) {
            return oldState
        }

        if (oldState.state == PlayerState.PRELOADING) {
            return oldState
        }

        return oldState.copy(state = PlayerState.PLAYING)
    }

    private suspend fun updatePlayerMetadata(oldState: ChipboxPlaybackState, newTrackId: Long): ChipboxPlaybackState {
        val newTrack = getTrack(newTrackId) ?: return oldState.copy(state = PlayerState.ERROR)
        if (oldState.state == PlayerState.PRELOADING) {
            metadataStateMutable.emit(newTrack)
        }
        return oldState
    }

    private fun handleSpeakerError(event: SpeakerEvent.Error, oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        emitError(event.message)

        directorScope.launch {
            speaker.stop()
            generator.stop()
        }

        return oldState.copy(state = PlayerState.ERROR, errorMessage = event.message)
    }

    private fun getTrack(id: Long) =
        repository.getTrack(id, withArtists = true, withGame = true)

    private fun emitError(message: String) {
        hatchet.e("Error: $message")
        currentState = currentState.copy(
            state = PlayerState.ERROR,
            errorMessage = message,
        )
    }
}