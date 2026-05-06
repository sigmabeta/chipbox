package net.sigmabeta.chipbox.player.speaker.real

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import androidx.media.AudioAttributesCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production [Speaker] that writes PCM to an Android [AudioTrack].
 *
 * The track is lazily (re)created whenever an incoming buffer's sample rate differs from the
 * current track's — emulators within a setlist may use different rates, so the speaker can't
 * commit to a single configuration up front. The consume coroutine is also bumped to
 * [Process.THREAD_PRIORITY_URGENT_AUDIO] when (re)initializing to avoid underruns under load.
 */
class RealSpeaker(
        bufferManager: ConsumerBufferManager,
        private val hatchet: Hatchet,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Speaker(bufferManager, dispatcher) {
    private var audioTrack: AudioTrack? = null

    override fun onAudioReceived(audio: AudioBuffer) {
        if (audio.sampleRate != audioTrack?.sampleRate) {
            hatchet.d("New sample rate: ${audio.sampleRate}")
            audioTrack = initializeAudioTrack(audio.sampleRate)
            hatchet.d("Audiotrack setup complete!")

            audioTrack!!.play()
        }

        // Samples, not Frames
        val samplesWritten = audioTrack!!.write(
                audio.data,
                0,
                audio.data.size
        )

        logProblems(samplesWritten)
    }

    private fun initializeAudioTrack(
            sampleRate: Int,
    ): AudioTrack {
        teardown()
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val bufferSizeBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        )

        hatchet.v("Initializing audio track. Sample Rate: $sampleRate Hz, Buffer size: $bufferSizeBytes bytes")

        val audioAttributes = AudioAttributesCompat.Builder().apply {
            setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            setUsage(AudioAttributesCompat.USAGE_MEDIA)
        }.build()

        val audioFormat = AudioFormat.Builder().apply {
            setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            setSampleRate(sampleRate)
            setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        }.build()

        return AudioTrack.Builder().apply {
            setAudioAttributes(audioAttributes.unwrap() as AudioAttributes)
            setAudioFormat(audioFormat)
            setBufferSizeInBytes(bufferSizeBytes)
            setTransferMode(AudioTrack.MODE_STREAM)
        }.build()
    }

    override fun onPaused() {
        audioTrack?.pause()
    }

    override fun onResumed() {
        audioTrack?.play()
    }

    override fun teardown() {
        if (audioTrack != null) {
            hatchet.i("Tearing down audiotrack.")
        }

        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()

        audioTrack = null
    }

    private fun logProblems(samplesWritten: Int) {
        val error = when (samplesWritten) {
            AudioTrack.ERROR_INVALID_OPERATION -> "Invalid AudioTrack operation."
            AudioTrack.ERROR_BAD_VALUE -> "Invalid AudioTrack value."
            AudioTrack.ERROR -> "Unknown AudioTrack error."
            else -> null
        }

        if (error != null) {
            hatchet.e(error)
            emitError(error)
        }
    }
}
