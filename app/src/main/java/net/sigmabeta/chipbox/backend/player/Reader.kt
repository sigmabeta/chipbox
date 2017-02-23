package net.sigmabeta.chipbox.backend.player

import android.media.session.PlaybackState
import net.sigmabeta.chipbox.backend.Backend
import net.sigmabeta.chipbox.model.audio.AudioBuffer
import net.sigmabeta.chipbox.model.audio.AudioConfig
import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.util.*
import java.io.File
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class Reader(val player: Player,
             val playlist: Playlist,
             val repository: Repository,
             val audioConfig: AudioConfig,
             val emptyBuffers: BlockingQueue<AudioBuffer>,
             val fullBuffers: BlockingQueue<AudioBuffer>,
             var queuedTrackId: String?,
             var resuming: Boolean) {
    var backend: Backend? = null

    var playingTrackId: String? = null
        set (value) {
            if (value != null) {
                val track = repository.getTrackSync(value)

                if (track != null) {
                    if (!resuming) {
                        backend?.teardown()

                        loadTrack(track,
                                audioConfig.sampleRate,
                                audioConfig.bufferSizeShorts.toLong())
                    } else {
                        resuming = false
                    }
                }

                player.onTrackChange(value, track?.game?.id)
            }

            field = value
        }

    var queuedSeekPosition: Long? = null

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
                backend?.seek(it)
                player.onPlaybackPositionUpdate(it.toLong())
                queuedSeekPosition = null
            }

            if (backend?.isTrackOver() ?: true) {
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
                backend?.readNextSamples(audioBuffer.buffer)
            }

            val error = backend?.getLastError()

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

        if (player.state != PlaybackState.STATE_PAUSED) {
            player.onPlaybackPositionUpdate(0)
        }

        emptyBuffers.clear()

        repository.close()

        logVerbose("[Player] Reader loop has ended.")
    }

    /**
     * Private Methods
     */

    private fun loadTrack(track: Track, sampleRate: Int, bufferSizeShorts: Long) {
        val backendId = track.backendId

        if (backendId == null) {
            logError("Bad backend ID.")
            return
        }

        val path = track.path.orEmpty()

        logDebug("[PlayerNative] Loading file: ${path}")

        val extension = File(path).extension
        val trackNumber = if (EXTENSIONS_MULTI_TRACK.contains(extension)) {
            (track.trackNumber ?: 1) - 1
        } else {
            0
        }

        backend = Backend.IMPLEMENTATIONS[backendId]
        backend?.loadFile(path, trackNumber, sampleRate, bufferSizeShorts, track.trackLength ?: 60000)

        val loadError = backend?.getLastError()

        if (loadError != null) {
            logError("[PlayerNative] Unable to load file: $loadError")
        }
    }
}
