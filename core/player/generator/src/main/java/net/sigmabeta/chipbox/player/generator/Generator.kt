package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.GeneratorEvent
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.player.resampler.JvmResampler
import net.sigmabeta.chipbox.repository.Repository

abstract class Generator(
    private val repository: Repository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    abstract val emulatorSampleRate: Int

    private val generatorScope = CoroutineScope(dispatcher)

    private var ongoingGenerationJob: Job? = null

    private var framesPlayed = 0

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
                emulatorSampleRate,
                outputSampleRate
            )

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

    private suspend fun playTrack(track: Track) {
        var error: String? = null

        var buffersCreated = 0

        val generatedAudio = createEmulatorBuffer(resampler.inputLengthShorts)
        val resampledAudio = createEmulatorBuffer(resampler.outputLengthShorts)

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

            FadeProcessor.fadeIfNecessary(
                generatedAudio,
                emulatorSampleRate,
                bufferStartFrame.framesToMillis(emulatorSampleRate),
                track.trackLengthMs - LENGTH_FADE_MILLIS,
                LENGTH_FADE_MILLIS
            )

            val resampleBuffer = resampler.resample(generatedAudio)
            resampleBuffer.copyInto(resampledAudio)

            // TODO This should block if generator is too far ahead of speaker.
            // Emit this buffer.
            bufferFlow.emit(
                GeneratorEvent.Audio(
                    buffersCreated++,
                    bufferStartFrame.framesToMillis(emulatorSampleRate),
                    framesPlayed,
                    emulatorSampleRate,
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

    private fun createEmulatorBuffer(sizeShorts: Int): ShortArray {
        return ShortArray(sizeShorts)
    }

    companion object {
        private const val LENGTH_FADE_MILLIS = 6_000.0
    }
}