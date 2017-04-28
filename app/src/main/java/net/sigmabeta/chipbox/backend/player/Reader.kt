package net.sigmabeta.chipbox.backend.player

import android.media.session.PlaybackState
import net.sigmabeta.chipbox.backend.Backend
import net.sigmabeta.chipbox.model.audio.AudioBuffer
import net.sigmabeta.chipbox.model.audio.AudioConfig
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.util.EXTENSIONS_MULTI_TRACK
import timber.log.Timber
import java.io.File
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Provider

class Reader(val player: Player,
             val playlist: Playlist,
             val repositoryProvider: Provider<Repository>,
             val audioConfig: AudioConfig,
             val emptyBuffers: BlockingQueue<AudioBuffer>,
             val fullBuffers: BlockingQueue<AudioBuffer>,
             var queuedTrackId: String?,
             var resuming: Boolean) {
    var backend: Backend? = null

    var repository: Repository? = null

    var playingTrackId: String? = null
        set (value) {
            if (value != null) {
                val track = repository?.getTrackSync(value)

                if (track != null) {
                    if (!resuming) {
                        backend?.teardown()

                        loadTrack(track,
                                audioConfig.sampleRate,
                                audioConfig.bufferSizeShorts.toLong())

                        resetEmptyBuffers()
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
        repository = repositoryProvider.get()

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
                Timber.v("Track has ended.")

                val nextFullBuffer = fullBuffers.peek()
                if (nextFullBuffer == null) {
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
                } else {
                    Timber.i("Writer still outputting finished track.")
                    Thread.sleep(audioConfig.minimumLatency.toLong())
                    continue
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

        if (player.state != PlaybackState.STATE_PAUSED) {
            player.onPlaybackPositionUpdate(0)
        }

        repository?.close()

        Timber.v("Reader loop has ended.")
    }

    /**
     * Private Methods
     */

    private fun loadTrack(track: Track, sampleRate: Int, bufferSizeShorts: Long) {
        val backendId = track.backendId

        if (backendId == null) {
            Timber.e("Bad backend ID.")
            return
        }

        val path = track.path.orEmpty()

        Timber.d("Loading file: %s", path)

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
            Timber.e("Unable to load file: %s", loadError)
        }
    }

    private fun resetEmptyBuffers() {
        Timber.i("Resetting empty buffers; %d missing", emptyBuffers.remainingCapacity())
        var returnedBuffers = 0
        while (emptyBuffers.remainingCapacity() > 0) {
            var nextBuffer = fullBuffers.poll()

            if (nextBuffer == null) {
                nextBuffer = AudioBuffer(audioConfig.bufferSizeShorts)
            } else {
                returnedBuffers++
            }

            emptyBuffers.add(nextBuffer)
        }
        Timber.i("Resetted %d buffers", returnedBuffers)
    }
}
