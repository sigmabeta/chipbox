package net.sigmabeta.chipbox.player.speaker.real

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import androidx.media.AudioAttributesCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import net.sigmabeta.chipbox.player.common.GeneratorEvent
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import timber.log.Timber

class RealSpeaker(
    private val outputSampleRate: Int,
    private val outputBufferSizeBytes: Int,
    private val generator: Generator,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Speaker {
    private val speakerScope = CoroutineScope(dispatcher)

    private var ongoingPlaybackJob: Job? = null

    override suspend fun play(trackId: Long) {
        startPlayback(trackId)
    }

    private suspend fun startPlayback(trackId: Long) {
        if (ongoingPlaybackJob == null) {
            ongoingPlaybackJob = speakerScope.launch {
                val audioTrack = initializeAudioTrack(outputSampleRate, outputBufferSizeBytes)
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                audioTrack.play()

                generator
                        .audioStream()
                        .collect {
                            when (it) {
                                GeneratorEvent.Loading -> onPlaybackLoading()
                                GeneratorEvent.Complete -> onPlaybackComplete(audioTrack)
                                is GeneratorEvent.Error -> onPlaybackError(audioTrack, it.message)
                                is GeneratorEvent.Audio -> {
                                    onAudioGenerated(it.data, audioTrack)
                                    return@collect
                                }
                            }
                        }
            }
        }

        generator.startTrack(
                trackId,
                outputSampleRate,
                outputBufferSizeBytes
        )
    }

    private fun stopPlayback() {
        ongoingPlaybackJob?.cancel()
        ongoingPlaybackJob = null
    }

    private fun onPlaybackLoading() {
        Timber.d("Generator reports track loading.")
    }

    private fun onPlaybackComplete(audioTrack: AudioTrack) {
        Timber.d("Generator reports track complete.")
        teardown(audioTrack)
        stopPlayback()
    }

    private fun onPlaybackError(audioTrack: AudioTrack, message: String) {
        Timber.e("Generator reports playback error: $message")
        teardown(audioTrack)
        stopPlayback()
    }

    private fun onAudioGenerated(audio: ShortArray, audioTrack: AudioTrack) {
        // Samples, not Frames
        val samplesWritten = audioTrack.write(
            audio,
            0,
            audio.size
        )
        logProblems(samplesWritten)
        generator.returnBuffer(audio)
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
        if (samplesWritten == outputBufferSizeBytes / 2)
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


