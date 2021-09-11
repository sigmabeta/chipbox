package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.*
import net.sigmabeta.chipbox.player.resampler.JvmResampler
import net.sigmabeta.chipbox.repository.Repository
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

abstract class Generator(
    private val repository: Repository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    abstract fun getEmulatorSampleRate(): Int

    private val generatorScope = CoroutineScope(dispatcher)

    private var ongoingGenerationJob: Job? = null

    private var framesPlayed = 0

    private var outputBuffers: ArrayBlockingQueue<ShortArray>? = null

    private val bufferFlow = MutableSharedFlow<GeneratorEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )

    private lateinit var resampler: JvmResampler

    abstract fun loadTrack(loadedTrack: Track)

    abstract fun generateAudio(buffer: ShortArray): Int

    abstract fun teardown()

    abstract fun isTrackOver(): Boolean

    abstract fun getLastError(): String?

    fun audioStream(
        trackId: Long,
        outputSampleRate: Int,
        outputBufferSizeBytes: Int
    ): Flow<GeneratorEvent> {
        if (ongoingGenerationJob != null) {
            ongoingGenerationJob?.cancel()
        }

        println("Output buffer size: $outputBufferSizeBytes bytes")

        ongoingGenerationJob = generatorScope.launch {
            bufferFlow.emit(GeneratorEvent.Loading)

            val loadedTrack = repository.getTrack(trackId)

            if (loadedTrack == null) {
                val error = "Could not find track with id $trackId."
                emitError(error)
                cancel(error)
                return@launch
            }

            loadTrack(loadedTrack)

            resampler = JvmResampler(
                outputBufferSizeBytes / 2,
                getEmulatorSampleRate(),
                outputSampleRate
            )

            val bufferCount = BUFFER_LENGTH_MILLIS
                .millisToFrames(outputSampleRate)
                .framesToSamples()
                .samplesToBytes()
                .div(outputBufferSizeBytes)

            setupBuffers(bufferCount)

            val error = getLastError()

            if (error != null) {
                emitError(error)
                cancel(error)
                return@launch
            }

            playTrack(loadedTrack)

            ongoingGenerationJob = null
        }

        return bufferFlow.asSharedFlow()
    }

    fun returnBuffer(audio: ShortArray) {
        audio.clear()
        outputBuffers!!.put(audio)
    }

    private fun setupBuffers(bufferCount: Int) {
        outputBuffers = ArrayBlockingQueue(bufferCount)

        repeat(bufferCount) {
            outputBuffers!!.add(ShortArray(resampler.outputLengthShorts))
        }
    }

    private suspend fun playTrack(track: Track) {
        var error: String? = null

        var buffersCreated = 0

        val generatedAudio = ShortArray(resampler.inputLengthShorts)

        while (!isTrackOver()) {
            // Check if this coroutine has been cancelled.
            yield()

            // TODO We should have a pool of buffers we use instead of a new one evrytiem
            // Setup buffers.
            val bufferStartFrame = framesPlayed

            // Generate the next buffer of audio..
            framesPlayed += generateAudio(generatedAudio)

            // TODO This only reports errors if the *first* buffer generation fails. Do better!
            if (framesPlayed == 0) {
                error = "Failed to generate any audio."
                break
            }

            val sampleRate = getEmulatorSampleRate()

            FadeProcessor.fadeIfNecessary(
                generatedAudio,
                sampleRate,
                bufferStartFrame.framesToMillis(sampleRate),
                track.trackLengthMs - LENGTH_FADE_MILLIS,
                LENGTH_FADE_MILLIS
            )

            val resampleBuffer = resampler.resample(generatedAudio)

            val resampledAudio =
                outputBuffers!!.poll(TIMEOUT_BUFFERS_FULL_MS, TimeUnit.MILLISECONDS)
            resampleBuffer.copyInto(resampledAudio)

            // Emit this buffer.
            bufferFlow.emit(
                GeneratorEvent.Audio(
                    buffersCreated++,
                    bufferStartFrame.framesToMillis(sampleRate),
                    framesPlayed,
                    sampleRate,
                    resampledAudio
                )
            )
        }

        teardown()

        // Report error, if it happened.
        if (error != null) {
            emitError(error)
            return
        }

        // If no error, report completion.
        bufferFlow.emit(GeneratorEvent.Complete)
    }

    private suspend fun emitError(error: String) {
        bufferFlow.emit(
            GeneratorEvent.Error(error)
        )
    }

    private fun ShortArray.clear() {
        forEachIndexed { index, _ -> set(index, 0) }
    }

    companion object {
        private const val LENGTH_FADE_MILLIS = 6_000.0

        private const val BUFFER_LENGTH_MILLIS = 500.0

        private const val TIMEOUT_BUFFERS_FULL_MS = 3000L
    }
}