package net.sigmabeta.chipbox.player.director.real

import kotlinx.coroutines.*
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.repository.Repository

class RealDirector(
    private val generator: Generator,
    private val speaker: Speaker,
    private val repository: Repository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Director {
    private val directorScope = CoroutineScope(dispatcher)

    private var currentSession: Session? = null

    private var setlist: List<Track>? = null

    override fun start(session: Session) {
        directorScope.launch {
            currentSession = session
            setlist = getSetlistForSession(session)

            val firstTrackId = setlist
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
                TODO("Emit Error Here")
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
    }

    override fun stop() {
        directorScope.launch {
            speaker.stop()
            generator.stop()
        }
    }

    private suspend fun startTrack(trackId: Long) {
        generator.startTrack(trackId)

        // TODO Speaker begins playing when generator reports buffers available
        delay(200)
        speaker.play()
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
}