package net.sigmabeta.chipbox.backend.player

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.session.PlaybackState
import android.os.Process
import android.util.Log
import net.sigmabeta.chipbox.backend.StatsManager
import net.sigmabeta.chipbox.model.audio.AudioBuffer
import net.sigmabeta.chipbox.model.audio.AudioConfig
import timber.log.Timber
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class Writer(val player: Player,
             val audioConfig: AudioConfig,
             val audioManager: AudioManager,
             val emptyBuffers: BlockingQueue<AudioBuffer>,
             val fullBuffers: BlockingQueue<AudioBuffer>) {
    var ducking = false

    val stats = StatsManager(audioConfig)

    var audioTrack: AudioTrack? = null

    fun loop() {
        Timber.d("Starting writer loop.")

        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        audioTrack = initializeAudioTrack()
        var duckVolume = 1.0f
        var writerIndex = 0

        // Begin playback loop
        audioTrack?.play()

        val timeout = 5000L

        while (player.state == PlaybackState.STATE_PLAYING) {
            var audioBuffer = fullBuffers.poll()

            if (audioBuffer == null) {
                Timber.e("Buffer underrun.")
                stats.underrunCount += 1

                audioBuffer = fullBuffers.poll(timeout, TimeUnit.MILLISECONDS)

                if (audioBuffer == null) {
                    Timber.e("Couldn't get a full buffer after %d ms; stopping...", timeout)
                    player.state = PlaybackState.STATE_ERROR
                    break
                }
            }

            // Check if necessary to make volume adjustments
            if (ducking) {
                Timber.d("Ducking behind other app...")

                if (duckVolume > 0.3f) {
                    duckVolume -= 0.4f
                    Timber.v("Lowering volume to %.2f...", duckVolume)
                }

                audioTrack?.setVolume(duckVolume)
            } else {
                if (duckVolume < 1.0f) {
                    duckVolume += 0.1f
                    Timber.v("Raising volume to %.2f...", duckVolume)

                    audioTrack?.setVolume(duckVolume)
                }
            }

            val bytesWritten = audioTrack?.write(audioBuffer.buffer, 0, audioConfig.bufferSizeShorts)
                    ?: Player.ERROR_AUDIO_TRACK_NULL

            emptyBuffers.put(audioBuffer)

            logProblems(bytesWritten)

            writerIndex += 1
            if (writerIndex == Player.READ_AHEAD_BUFFER_SIZE) {
                writerIndex = 0
            }
        }


        logStats()
        stats.clear()

        Timber.v("Clearing full buffer queue...")
        fullBuffers.clear()

        audioTrack?.pause()
        audioTrack?.release()

        Timber.v("Writer loop has ended.")
    }

    fun clearBuffers() {
        Timber.i("Resetting full buffers.")

        audioTrack?.flush()

        while (true) {
            try {
                val nextFullBuffer = fullBuffers.poll()

                if (nextFullBuffer != null) {
                    emptyBuffers.add(nextFullBuffer)
                } else {
                    break
                }
            } catch (ex: IllegalStateException) {
                Timber.e("Empty buffers filled before full buffers cleared.")
                Timber.e(Log.getStackTraceString(ex))
                fullBuffers.clear()
                break
            }
        }
    }

    /**
     * Private Methods
     */

    private fun initializeAudioTrack(): AudioTrack {
        Timber.v("Initializing audio track.\n" +
                "Sample Rate: %d Hz\n" +
                "Buffer size: %d bytes\n" +
                "Buffer length: %d msec",
                audioConfig.sampleRate,
                audioConfig.bufferSizeBytes,
                audioConfig.minimumLatency * Player.READ_AHEAD_BUFFER_SIZE)

        val audioTrack = AudioTrack(AudioManager.STREAM_MUSIC,
                audioConfig.sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioConfig.bufferSizeBytes,
                AudioTrack.MODE_STREAM)

        // Get updates on playback position every second (one frame is equal to one sample).
        audioTrack.positionNotificationPeriod = audioConfig.sampleRate

        // Set a listener to update the UI's playback position.
        audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onPeriodicNotification(track: AudioTrack) {
                val millisPlayed = player.backend?.getMillisPlayed() ?: 0L
                player.onPlaybackPositionUpdate(millisPlayed)
            }

            override fun onMarkerReached(track: AudioTrack) {}
        })

        return audioTrack
    }

    private fun logProblems(bytesWritten: Int) {
        if (bytesWritten == audioConfig.bufferSizeShorts)
            return

        val error = when (bytesWritten) {
            AudioTrack.ERROR_INVALID_OPERATION -> "Invalid AudioTrack operation."
            AudioTrack.ERROR_BAD_VALUE -> "Invalid AudioTrack value."
            AudioTrack.ERROR -> "Unknown AudioTrack error."
            Player.ERROR_AUDIO_TRACK_NULL -> "No audio track found."
            else -> "Wrote fewer bytes than expected: $bytesWritten"
        }

        Timber.e(error)
    }

    private fun logStats() {
        Timber.i("Underruns since playback started: %d", stats.underrunCount)
    }
}