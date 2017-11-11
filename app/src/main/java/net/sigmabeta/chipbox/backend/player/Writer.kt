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

    var seeking = false

    val stats = StatsManager(audioConfig)

    var audioTrack: AudioTrack? = null

    var lastTimestamp = -1L

    fun loop() {
        Timber.d("Starting writer loop.")

        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        audioTrack = initializeAudioTrack()
        var duckVolume = 1.0f
        var writerIndex = 0

        // Begin playback loop
        audioTrack?.play()

        val timeout = 5000L

        try {
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

                if (audioBuffer.timeStamp < 0L) {
                    throw RuntimeException("Invalid timestamp: ${audioBuffer.timeStamp}")
                }

                val bytesWritten = audioTrack?.write(audioBuffer.buffer, 0, audioConfig.singleBufferSizeShorts)
                        ?: Player.ERROR_AUDIO_TRACK_NULL

                if (seeking) {
                    clearBuffers()
                    seeking = false
                } else {
                    player.onPlaybackPositionUpdate(audioBuffer.timeStamp)

                    if (lastTimestamp < audioBuffer.timeStamp) {
//                Timber.w("Playing buffer timestamped at %d", audioBuffer.timeStamp)
                    } else if (audioBuffer.timeStamp > 0) {
                        Timber.e("Buffer timestamp timing problem: %d > %d", lastTimestamp, audioBuffer.timeStamp)
                    } else {
                        Timber.e("Buffer timestamp timing problem: %d is negative", audioBuffer.timeStamp)
                    }
                }

                lastTimestamp = audioBuffer.timeStamp
                audioBuffer.timeStamp = -1L

                emptyBuffers.put(audioBuffer)

                logProblems(bytesWritten)

                writerIndex += 1
                if (writerIndex == audioConfig.bufferCount) {
                    writerIndex = 0
                }
            }
        } catch (ex: InterruptedException) {
            Timber.d("Writer thread interrupted.")
        }

        logStats()
        stats.clear()

        Timber.v("Clearing full buffer queue...")
        clearBuffers()

        audioTrack?.pause()
        audioTrack?.release()

        Timber.v("Writer loop has ended.")
    }

    fun onSeek(millisPlayed: Long) {
        seeking = true
    }

    fun clearBuffers() {
        Timber.i("Resetting full buffers.")

        audioTrack?.flush()

        var clearedBuffers = 0
        while (true) {
            try {
                val nextFullBuffer = fullBuffers.poll()

                if (nextFullBuffer != null) {
                    Timber.v("Clearing full buffer...")
                    clearedBuffers++
                    nextFullBuffer.buffer.fill(0)
                    nextFullBuffer.timeStamp = -1L
                    emptyBuffers.add(nextFullBuffer)
                } else {
                    Timber.d("No full buffers remaining.")
                    break
                }
            } catch (ex: IllegalStateException) {
                Timber.e("Empty buffers filled before full buffers cleared.")
                Timber.e(Log.getStackTraceString(ex))
                fullBuffers.clear()
                break
            }
        }

        Timber.d("Cleared %d buffers.", clearedBuffers)
    }

    /**
     * Private Methods
     */

    private fun initializeAudioTrack(): AudioTrack {
        Timber.v("Initializing audio track.\n" +
                "Sample Rate: %d Hz\n" +
                "Single Buffer size: %d bytes\n" +
                "Single Buffer length: %d msec",
                audioConfig.sampleRate,
                audioConfig.singleBufferSizeBytes,
                audioConfig.singleBufferLatency)

        val audioTrack = AudioTrack(AudioManager.STREAM_MUSIC,
                audioConfig.sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioConfig.singleBufferSizeBytes,
                AudioTrack.MODE_STREAM)

        return audioTrack
    }

    private fun logProblems(bytesWritten: Int) {
        if (bytesWritten == audioConfig.singleBufferSizeShorts)
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