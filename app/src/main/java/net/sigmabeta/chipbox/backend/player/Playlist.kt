package net.sigmabeta.chipbox.backend.player

import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.repository.Repository
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Playlist @Inject constructor(val repository: Repository,
                                   val updater: UiUpdater) {

    var playbackQueuePosition: Int = 0
    var actualPlaybackQueuePosition: Int = 0

    var shuffledPositionQueue: MutableList<Int>? = null

    var playbackQueue: MutableList<String?> = ArrayList<String?>(0)
        set (value) {
            field = value

            if (shuffle) {
                generateRandomPositionArray()
            }
        }

    var playingTrackId: String? = null
        set (value) {
            field = value

            if (value != null) {
                updater.send(TrackEvent(value))
            } else {
                playingGameId = null
            }

        }

    var playingGameId: String? = null
        set (value) {
            if (field != value) {
                field = value
                updater.send(GameEvent(value))
            }
        }

    var shuffle = false
        set (value) {
            if (value == true) {
                generateRandomPositionArray()
            } else {
                playbackQueuePosition = shuffledPositionQueue?.get(playbackQueuePosition) ?: 0
                shuffledPositionQueue = null
            }
            field = value
        }

    var repeat = Player.REPEAT_OFF

    /**
     * Public Methods
     */

    fun isNextTrackAvailable() = playbackQueuePosition < playbackQueue.size - 1 || repeat != Player.REPEAT_OFF

    fun getNextTrack(): String? {
        if (playbackQueuePosition < playbackQueue.size - 1) {
            playbackQueuePosition += 1
        } else if (repeat == Player.REPEAT_ALL) {
            playbackQueuePosition = 0
        } else {
            return null
        }

        Timber.i("Loading track %d of %d.", playbackQueuePosition, playbackQueue.size)
        return getTrackIdAt(playbackQueuePosition)
    }

    fun getPrevTrack(): String? {
        if (playbackQueuePosition > 0) {
            playbackQueuePosition -= 1
        } else {
            return null
        }

        Timber.i("Loading track %d of %d.", playbackQueuePosition, playbackQueue.size)
        return getTrackIdAt(playbackQueuePosition)
    }

    fun getTrackIdAt(position: Int, ignoreShuffle: Boolean = false): String? {
        if (position > playbackQueue.size) {
            Timber.e("Requested invalid position: %d / %d", position, playbackQueue.size)
            return null
        }

        val actualPosition = if (shuffle && !ignoreShuffle) {
            shuffledPositionQueue?.get(position) ?: 0
        } else {
            position
        }

        actualPlaybackQueuePosition = actualPosition
        return playbackQueue.get(actualPosition)
    }

    fun onTrackMoved(originPos: Int, destPos: Int) {
        Collections.swap(playbackQueue, originPos, destPos)

        if (originPos == playbackQueuePosition) {
            playbackQueuePosition = destPos
        } else if (destPos == playbackQueuePosition) {
            playbackQueuePosition = originPos
        }
    }

    fun onTrackRemoved(position: Int) {
        playbackQueue.removeAt(position)

        if (position == playbackQueuePosition) {
            // TODO Come up with a way to end current track
        } else if (position < playbackQueuePosition) {
            playbackQueuePosition = playbackQueuePosition - 1
        }
    }

    /**
     * Private Methods
     */

    private fun generateRandomPositionArray() {
        // Creates an array where position 1's value is 1, 267's value is 267, etc
        val possiblePositions = Array(playbackQueue.size) { position -> position }.toMutableList()
        Collections.shuffle(possiblePositions)

        shuffledPositionQueue = possiblePositions
    }
}