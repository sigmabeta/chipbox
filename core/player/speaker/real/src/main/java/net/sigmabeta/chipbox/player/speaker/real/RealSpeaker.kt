package net.sigmabeta.chipbox.player.speaker.real

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.media.AudioAttributesCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import timber.log.Timber

class RealSpeaker(
    private val sampleRate: Int,
    private val bufferSizeBytes: Int,
    private val generator: Generator,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Speaker {
    private val speakerScope = CoroutineScope(dispatcher)

    private val ongoingPlaybackJob: Job? = null

    override suspend fun play(trackId: Long) {
        ongoingPlaybackJob?.cancelAndJoin()
        speakerScope.launch {
            val audioTrack = initializeAudioTrack(sampleRate, bufferSizeBytes)
            playAudioFromGenerator(audioTrack)
        }
    }

    private suspend fun playAudioFromGenerator(audioTrack: AudioTrack) {
        audioTrack.play()

        generator
            .audioStream()
            .onCompletion { teardown(audioTrack) }
            .collect {
                // Samples, not Frames
                val samplesWritten = audioTrack.write(
                    it.data,
                    0,
                    bufferSizeBytes / 2
                )
                logProblems(samplesWritten)
            }
    }

    private fun teardown(audioTrack: AudioTrack) {
        audioTrack.pause()
        audioTrack.release()
    }

    private fun initializeAudioTrack(
        sampleRate: Int,
        bufferSizeBytes: Int
    ): AudioTrack {
        Timber.v(
            "Initializing audio track.\n Sample Rate: %d Hz\n Buffer size: %d bytes\n",
            sampleRate,
            bufferSizeBytes
        )

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

    private fun logProblems(samplesWritten: Int) {
        if (samplesWritten == bufferSizeBytes / 2)
            return

        val error = when (samplesWritten) {
            AudioTrack.ERROR_INVALID_OPERATION -> "Invalid AudioTrack operation."
            AudioTrack.ERROR_BAD_VALUE -> "Invalid AudioTrack value."
            AudioTrack.ERROR -> "Unknown AudioTrack error."
            else -> "Wrote fewer bytes than expected: $samplesWritten"
        }

        Timber.e(error)
    }
}


