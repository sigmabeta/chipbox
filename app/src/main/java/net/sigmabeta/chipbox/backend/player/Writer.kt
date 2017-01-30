package net.sigmabeta.chipbox.backend.player

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.session.PlaybackState
import android.os.Process
import net.sigmabeta.chipbox.backend.StatsManager
import net.sigmabeta.chipbox.model.audio.AudioBuffer
import net.sigmabeta.chipbox.model.audio.AudioConfig
import net.sigmabeta.chipbox.util.external.getMillisPlayed
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class Writer(val player: Player,
             val audioConfig: AudioConfig,
             val audioManager: AudioManager,
             val emptyBuffers: BlockingQueue<AudioBuffer>,
             val fullBuffers: BlockingQueue<AudioBuffer>) {
    var ducking = false

    val stats = StatsManager(audioConfig)

    fun loop() {
        logDebug("[Player] Starting writer loop.")

        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val audioTrack = initializeAudioTrack()
        var duckVolume = 1.0f
        var writerIndex = 0

        // Begin playback loop
        audioTrack.play()

        val timeout = 5000L

        while (player.state == PlaybackState.STATE_PLAYING) {
            var audioBuffer = fullBuffers.poll()

            if (audioBuffer == null) {
                logError("[Player] Buffer underrun.")
                stats.underrunCount += 1

                audioBuffer = fullBuffers.poll(timeout, TimeUnit.MILLISECONDS)

                if (audioBuffer == null) {
                    logError("[Player] Couldn't get a full buffer after ${timeout}ms; stopping...")
                    player.state = PlaybackState.STATE_ERROR
                    break
                }
            }

            // Check if necessary to make volume adjustments
            if (ducking) {
                logDebug("[Player] Ducking behind other app...")

                if (duckVolume > 0.3f) {
                    duckVolume -= 0.4f
                    logVerbose("[Player] Lowering volume to $duckVolume...")
                }

                audioTrack.setVolume(duckVolume)
            } else {
                if (duckVolume < 1.0f) {
                    duckVolume += 0.1f
                    logVerbose("[Player] Raising volume to $duckVolume...")

                    audioTrack.setVolume(duckVolume)
                }
            }

            val bytesWritten = audioTrack.write(audioBuffer.buffer, 0, audioConfig.bufferSizeShorts)
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

        logVerbose("[Player] Clearing full buffer queue...")
        fullBuffers.clear()

        audioTrack.pause()
        audioTrack.flush()
        audioTrack.release()

        logVerbose("[Player] Writer loop has ended.")
    }


    /**
     * Private Methods
     */

    private fun initializeAudioTrack(): AudioTrack {
        logVerbose("[Player] Initializing audio track.\n" +
                "[Player] Sample Rate: ${audioConfig.sampleRate}Hz\n" +
                "[Player] Buffer size: ${audioConfig.bufferSizeBytes} bytes\n" +
                "[Player] Buffer length: ${audioConfig.minimumLatency * Player.READ_AHEAD_BUFFER_SIZE} msec")

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
                val millisPlayed = getMillisPlayed()
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
            else -> "Wrote fewer bytes than expected: ${bytesWritten}"
        }

        logError("[Player] $error")
    }

    private fun logStats() {
        logInfo("[Player] Underruns since playback started: ${stats.underrunCount}")
    }
}