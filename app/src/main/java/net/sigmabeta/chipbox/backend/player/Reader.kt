package net.sigmabeta.chipbox.backend.player

import android.media.session.PlaybackState
import net.sigmabeta.chipbox.model.audio.AudioBuffer
import net.sigmabeta.chipbox.model.audio.AudioConfig
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.util.external.*
import net.sigmabeta.chipbox.util.logVerbose
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class Reader(val player: Player,
             val playlist: Playlist,
             val repository: Repository,
             val audioConfig: AudioConfig,
             val emptyBuffers: BlockingQueue<AudioBuffer>,
             val fullBuffers: BlockingQueue<AudioBuffer>,
             val trackId: String) {

    var queuedTrackId: String? = trackId

    var playingTrackId: String? = null
        set (value) {
            teardown()

            if (value != null) {
                val track = repository.getTrackSync(value)

                if (track != null) {
                    loadTrackNative(track,
                            audioConfig.sampleRate,
                            audioConfig.bufferSizeShorts.toLong())
                }

                player.onTrackChange(value, track?.game?.id)
            }

            field = value
        }

    var queuedSeekPosition: Int? = null

    fun loop() {
        // Pre-seed the emptyQueue.
        while (true) {
            try {
                emptyBuffers.add(AudioBuffer(audioConfig.bufferSizeShorts))
            } catch (ex: IllegalStateException) {
                break
            }
        }

        while (player.state == PlaybackState.STATE_PLAYING) {
            queuedTrackId?.let {
                playingTrackId = it
                queuedTrackId = null
            }

            queuedSeekPosition?.let {
                seekNative(it)
                player.onPlaybackPositionUpdate(it.toLong())
                queuedSeekPosition = null
            }

            if (isTrackOver()) {
                logVerbose("[Player] Track has ended.")

                if (!playlist.isNextTrackAvailable()) {
                    player.onPlaylistFinished()
                    break
                } else {
                    if (playlist.repeat == Player.REPEAT_ONE) {
                        queuedTrackId = playingTrackId
                    } else {
                        queuedTrackId = playlist.getNextTrack()
                    }
                }
            }

            val audioBuffer = emptyBuffers.poll(Player.TIMEOUT_BUFFERS_FULL_MS, TimeUnit.MILLISECONDS)

            if (audioBuffer == null) {
                player.errorAllBuffersFull()
                break
            }

            // Get the next samples from the native player.
            synchronized(playingTrackId ?: break) {
                readNextSamples(audioBuffer.buffer)
            }

            val error = getLastError()

            if (error == null) {
                // Check this so that we don't put one last buffer into the full queue after it's cleared.
                if (player.state == PlaybackState.STATE_PLAYING) {
                    fullBuffers.put(audioBuffer)
                }
            } else {
                player.errorReadFailed(error)
                break
            }
        }

        logVerbose("[Player] Clearing empty buffer queue...")

        player.onPlaybackPositionUpdate(0)

        emptyBuffers.clear()

        repository.close()

        logVerbose("[Player] Reader loop has ended.")
    }
}