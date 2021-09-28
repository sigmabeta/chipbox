package net.sigmabeta.chipbox.player.speaker.real

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import androidx.media.AudioAttributesCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEmpty
import net.sigmabeta.chipbox.player.common.Dependencies
import net.sigmabeta.chipbox.player.common.GeneratorEvent
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import timber.log.Timber

class RealSpeaker(
    private val generator: Generator,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Speaker {
    private val speakerScope = CoroutineScope(dispatcher)

    private var ongoingPlaybackJob: Job? = null

    private var audioTrack: AudioTrack? = null

    override suspend fun play(trackId: Long) {
        startPlayback(trackId)
    }

    private fun startPlayback(trackId: Long) {
        if (ongoingPlaybackJob == null) {
            ongoingPlaybackJob = speakerScope.launch {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                generator
                        .audioStream()
                        .onCompletion { Timber.i("Flow complete.") }
                        .onEmpty { Timber.i("Flow empty.") }
                        .collect {
                            when (it) {
                                GeneratorEvent.Loading -> onPlaybackLoading()
                                GeneratorEvent.Complete -> onPlaybackComplete()
                                is GeneratorEvent.Error -> onPlaybackError(it.message)
                                is GeneratorEvent.Audio -> onAudioGenerated(it)
                            }
                        }

                Timber.w("Playback job ending.")
                ongoingPlaybackJob = null
            }
        }

        generator.startTrack(
                trackId
        )
    }

    private fun stopPlayback() {
        ongoingPlaybackJob?.cancel()
        ongoingPlaybackJob = null
    }

    private fun onPlaybackLoading() {
        Timber.d("Generator reports track loading.")
    }

    private fun onPlaybackComplete() {
        Timber.d("Generator reports track complete.")
        teardown()
        stopPlayback()
    }

    private fun onPlaybackError(message: String) {
        Timber.e("Generator reports playback error: $message")
        teardown()
        stopPlayback()
    }

    private fun onAudioGenerated(event: GeneratorEvent.Audio) {
        if (event.sampleRate != audioTrack?.sampleRate) {
            Timber.d("New sample rate: ${event.sampleRate}")
            audioTrack = initializeAudioTrack(event.sampleRate)
            Timber.d("Audiotrack setup complete!")

            audioTrack!!.play()
        }

        val audio = event.data

        // Samples, not Frames
        val samplesWritten = audioTrack!!.write(
            audio,
            0,
            audio.size
        )

        logProblems(samplesWritten)
        generator.returnBuffer(audio)
    }

    private fun initializeAudioTrack(
        sampleRate: Int,
    ): AudioTrack {
        teardown()
        val bufferSizeBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        )

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

    private fun teardown() {
        if (audioTrack != null) {
            Timber.i("Tearing down audiotrack.")
        }

        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()
    }

    private fun logProblems(samplesWritten: Int) {
        if (samplesWritten == Dependencies.BUFFER_SIZE_BYTES_DEFAULT / 2)
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


