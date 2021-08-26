package net.sigmabeta.chipbox.player.speaker.real

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.media.AudioAttributesCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import net.sigmabeta.chipbox.player.common.GeneratorEvent
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

    private var ongoingPlaybackJob: Job? = null

    override suspend fun play(trackId: Long) {
        ongoingPlaybackJob?.cancelAndJoin()
        ongoingPlaybackJob = speakerScope.launch {
            val audioTrack = initializeAudioTrack(sampleRate, bufferSizeBytes)
            playAudioFromGenerator(audioTrack, trackId)
        }
    }

    private suspend fun playAudioFromGenerator(audioTrack: AudioTrack, trackId: Long) {
        audioTrack.play()

        generator
            .audioStream(trackId)
            .collect {
                when (it) {
                    GeneratorEvent.Complete -> onPlaybackComplete(audioTrack)
                    GeneratorEvent.Error -> onPlaybackError(audioTrack)
                    is GeneratorEvent.Audio -> {
                        onAudioGenerated(it, audioTrack)
                        return@collect
                    }
                }

                ongoingPlaybackJob?.cancel()
            }
    }

    private fun onPlaybackComplete(audioTrack: AudioTrack) {
        Timber.d("Generator reports track complete.")
        teardown(audioTrack)
    }

    private fun onPlaybackError(audioTrack: AudioTrack) {
        Timber.e("Generator reports playback error.")
        teardown(audioTrack)
    }

    private fun onAudioGenerated(audio: GeneratorEvent.Audio, audioTrack: AudioTrack) {
        // Samples, not Frames
        val samplesWritten = audioTrack.write(
            audio.data,
            0,
            bufferSizeBytes / 2
        )
        logProblems(samplesWritten)
    }

    private fun teardown(audioTrack: AudioTrack) {
        Timber.i("Tearing down audiotrack.")
        audioTrack.pause()
        audioTrack.flush()
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


