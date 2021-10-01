package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
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

    private var currentSetlist: List<Track>? = null

    private var currentState: PlayerState = PlayerState.STOPPED

    init {
        directorScope.launch {
            generator.events().collect { currentState = reduce(currentState, it) }
        }

        directorScope.launch {
            speaker.events().collect { currentState = reduce(currentState, it) }
        }
    }

    override fun start(session: Session) {
        directorScope.launch {
            currentSession = session
            currentSetlist = getSetlistForSession(session)

            val firstTrackId = currentSetlist
                ?.get(session.startingPosition)
                ?.id

            if (firstTrackId != null) {
                currentSession = session.copy(
                    currentPosition = session.startingPosition
                )

                startTrack(
                    firstTrackId
                )
            } else {
                emitError("Couldn't find that track in the setlist.")
            }
        }
    }

    override fun play() {
        generator.play()
        speaker.play()
    }

    override fun pause() {
        generator.pause()
        speaker.pause()

        println("Playback paused.")
        currentState = PlayerState.PAUSED
    }

    override fun stop() {
        directorScope.launch {
            speaker.stop()
            generator.stop()
        }
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

            val nextTrackPosition = session.currentPosition + 1
            if (nextTrackPosition >= setlist.size) {
                // TODO This should also have a reducer.
                println("Generator requested next track, but no more exist.")
                currentState = PlayerState.ENDING
                generator.stop()
                return@launch
            }

            currentSession = session.copy(currentPosition = nextTrackPosition)
            val nextTrackId = setlist[nextTrackPosition].id

            startTrack(nextTrackId)
        }
    }

    private fun getSetlistForSession(session: Session) = when (session.type) {
        SessionType.GAME -> getTrackListForGame(session.contentId)
        SessionType.ARTIST -> getTrackListForArtist(session.contentId)
    }

    private fun getTrackListForGame(gameId: Long) = repository
        .getTracksForGame(gameId)

    private fun getTrackListForArtist(artistId: Long): List<Track> {
        TODO("Not yet implemented")
    }

    private fun reduce(oldState: PlayerState, event: GeneratorEvent) = when (event) {
            GeneratorEvent.Complete -> {
                nextTrack()
                oldState
            }
            is GeneratorEvent.Error -> {
                nextTrack()
                emitError(event.message)
                oldState
            }
            GeneratorEvent.Buffering -> PlayerState.BUFFERING
            GeneratorEvent.Emitting -> {
                if (oldState == PlayerState.BUFFERING) {
                    speaker.play()
                }
                oldState
            }
        }


    private fun reduce(oldState: PlayerState, event: SpeakerEvent) = when (event) {
            SpeakerEvent.Buffering -> handleSpeakerBuffering(oldState)
            SpeakerEvent.Playing -> handleSpeakerPlaying(oldState)
            is SpeakerEvent.TrackChange -> updatePlayerMetadata(oldState, event.trackId)
            is SpeakerEvent.Error -> {
                emitError(event.message)
                directorScope.launch {
                    speaker.stop()
                    generator.stop()
                }

                PlayerState.STOPPED
            }
        }

    private fun handleSpeakerBuffering(oldState: PlayerState): PlayerState {
        if (oldState == PlayerState.PLAYING) {
            println("Buffer underrun.")
            return oldState
        }

        if (oldState == PlayerState.ENDING) {
            println("Setlist complete.")
            stop()
            return PlayerState.STOPPED
        }

        emitError("SpeakerEvent.BUFFERING not expected in state $oldState.")
        return oldState
    }

    private fun handleSpeakerPlaying(oldState: PlayerState): PlayerState {
        if (oldState == PlayerState.BUFFERING) {
            println("Underrun resolved.")
        }

        if (oldState == PlayerState.ENDING) {
            return oldState
        }

        return PlayerState.PLAYING
    }

    private fun updatePlayerMetadata(oldState: PlayerState, newTrackId: Long): PlayerState {
        return oldState
    }

    private fun emitError(message: String) {
        println("Error: $message")
    }
}