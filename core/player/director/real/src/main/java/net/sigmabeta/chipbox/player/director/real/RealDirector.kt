package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
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

class RealDirector(
    private val generator: Generator,
    private val speaker: Speaker,
    private val repository: Repository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Director {
    private val directorScope = CoroutineScope(dispatcher)

    private var currentSession: Session? = null

    private var currentSetlist: List<Long>? = null

    private var currentState: ChipboxPlaybackState = ChipboxPlaybackState(
        PlayerState.IDLE,
        0,
        0,
        1.0f,
        false,
        null
    )
        set(value) {
            field = value
            directorScope.launch {
                playbackStateMutable.emit(currentState)
            }
        }

    private val metadataStateMutable = MutableSharedFlow<Track>()

    private val playbackStateMutable = MutableSharedFlow<ChipboxPlaybackState>()

    init {
        directorScope.launch {
            generator
                .events()
                .distinctUntilChanged()
                .collect {
                    println("Received GeneratorEvent: $it")
                    currentState = reduce(currentState, it)
                }
        }

        directorScope.launch {
            speaker
                .events()
                .distinctUntilChanged()
                .collect {
                    println("Received SpeakerEvent: $it")
                    currentState = reduce(currentState, it)
                }
        }
    }

    override fun start(session: Session) {
        directorScope.launch {
            val setlistForSession = getSetlistForSession(session)

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
            speaker.stop()

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

    override fun metadataState() = metadataStateMutable.asSharedFlow()

    override fun playbackState() = playbackStateMutable.asSharedFlow()

    override fun pauseTemporarily() {
        directorScope.launch {
            speaker.stop()
        }
    }

    /**
     * 🦆
     */
    override fun duck() {
        TODO("Not yet implemented")
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
                println("Generator requested next track, but no more exist.")
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
        GeneratorEvent.Emitting -> handleGeneratorEmitting(oldState, event)
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
                skipForwardAllowed = isCurrentTrackLastInSetlist(session, setlist)
            )

        }

        val newTrack = getTrack(event.trackId) ?: return oldState.copy(state = PlayerState.ERROR)
        metadataStateMutable.emit(newTrack)

        return oldState.copy(
            state = PlayerState.BUFFERING,
            skipForwardAllowed = isCurrentTrackLastInSetlist(session, setlist)
        )
    }

    private fun handleGeneratorEmitting(oldState: ChipboxPlaybackState, event: GeneratorEvent): ChipboxPlaybackState {
        if (oldState.state == PlayerState.BUFFERING) {
            speaker.play()
        }

        return oldState
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
        SpeakerEvent.Buffering -> handleSpeakerBuffering(oldState)
        SpeakerEvent.Playing -> handleSpeakerPlaying(oldState)
        is SpeakerEvent.TrackChange -> updatePlayerMetadata(oldState, event.trackId)
        is SpeakerEvent.Error -> handleSpeakerError(event, oldState)
    }

    private fun handleSpeakerBuffering(oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        if (oldState.state == PlayerState.PLAYING) {
            println("Buffer underrun.")
            return oldState
        }

        if (oldState.state == PlayerState.ENDING) {
            println("Setlist complete.")
            stop()
            return oldState.copy(state = PlayerState.STOPPED)
        }

//        emitError("SpeakerEvent.BUFFERING not expected in state $oldState.")
        return oldState
    }

    private fun handleSpeakerPlaying(oldState: ChipboxPlaybackState): ChipboxPlaybackState {
        if (oldState.state == PlayerState.BUFFERING) {
            println("Underrun resolved.")
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
        println("Error: $message")
    }
}