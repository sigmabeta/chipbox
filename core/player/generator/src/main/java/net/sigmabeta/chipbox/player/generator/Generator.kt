package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.*
import net.sigmabeta.chipbox.player.resampler.JvmResampler
import net.sigmabeta.chipbox.repository.Repository
import java.lang.IllegalStateException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

abstract class Generator(
        private val repository: Repository,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val generatorScope = CoroutineScope(dispatcher)

    private var ongoingGenerationJob: Job? = null

    private var framesPlayed = 0

    private var newTrackId: Long? = null

    private var currentTrack: Track? = null

    private var outputBuffers: ArrayBlockingQueue<ShortArray>? = null

    private val bufferFlow = MutableSharedFlow<GeneratorEvent>(
            replay = 0,
            onBufferOverflow = BufferOverflow.SUSPEND,
            extraBufferCapacity = 10
    )

    private lateinit var resampler: JvmResampler

    protected abstract fun loadTrack(loadedTrack: Track)

    protected abstract fun generateAudio(buffer: ShortArray): Int

    protected abstract fun teardown()

    protected abstract fun isTrackOver(): Boolean

    protected abstract fun getEmulatorSampleRate(): Int

    abstract fun getLastError(): String?

    fun audioStream() = bufferFlow.asSharedFlow()

    fun returnBuffer(audio: ShortArray) {
        audio.clear()
        outputBuffers!!.put(audio)
    }

    fun startTrack(
            trackId: Long,
            outputSampleRate: Int,
            outputBufferSizeBytes: Int
    ) {
        if (ongoingGenerationJob == null) {
            ongoingGenerationJob = generatorScope.launch {
                val trackToPlay = getTrackToPlay(trackId)

                if (trackToPlay == null) {
                    val error = "Could not find track with id $trackId."
                    emitError(error)
                    cancel(error)
                    return@launch
                }

                bufferFlow.emit(GeneratorEvent.Loading)

                loadTrack(trackToPlay)

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

                playTrack()
            }
        } else {
            newTrackId = trackId
        }
    }

    private fun getTrackToPlay(trackId: Long): Track? {
        if (currentTrack != null) {
            teardownHelper()
        }

        val loadedTrack = repository.getTrack(trackId)
        currentTrack = loadedTrack
        newTrackId = null

        return loadedTrack
    }

    private fun setupBuffers(bufferCount: Int) {
        outputBuffers = ArrayBlockingQueue(bufferCount)

        repeat(bufferCount) {
            outputBuffers!!.add(ShortArray(resampler.outputLengthShorts))
        }
    }

    private suspend fun playTrack() {
        var error: String? = null
        var track = currentTrack ?: throw IllegalStateException("No track loaded.")
        var buffersCreated = 0

        var generatedAudio = ShortArray(resampler.inputLengthShorts)

        while (!isTrackOver()) {
            // Check if this coroutine has been cancelled.
            yield()

            newTrackId?.let {
                val newTrack = getTrackToPlay(it)
                if (newTrack == null ) {
                    emitError("Failed to load track.")
                    return
                }

                track = newTrack
                loadTrack(newTrack)

                generatedAudio = setupResampler()
            }

            // Setup buffers.
            val bufferStartFrame = framesPlayed

            // Generate the next buffer of audio..
            val framesGenerated = generateAudio(generatedAudio)

            if (framesGenerated == 0) {
                error = "Failed to generate any audio."
                break
            }

            framesPlayed += framesGenerated

            error = getLastError()

            if (error != null) {
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

            yield()
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

        ongoingGenerationJob = null
    }

    private fun setupResampler(): ShortArray{
        resampler = JvmResampler(
                resampler.outputLengthShorts,
                getEmulatorSampleRate(),
                resampler.outputSampleRate
        )

        return ShortArray(resampler.inputLengthShorts)
    }

    private fun teardownHelper() {
        teardown()
        currentTrack = null
        framesPlayed = 0
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