package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.*
import net.sigmabeta.chipbox.repository.Repository
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
    ) {
        newTrackId = trackId

        if (ongoingGenerationJob == null) {
            ongoingGenerationJob = generatorScope.launch {
                playTrack()
            }
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

    private fun setupBuffers(sampleRate: Int) {
        val bufferCount = BUFFER_LENGTH_MILLIS
                .millisToFrames(sampleRate)
                .framesToSamples()
                .samplesToBytes()
                .div(Dependencies.BUFFER_SIZE_BYTES_DEFAULT)

        outputBuffers = ArrayBlockingQueue(bufferCount)

        val bufferSizeShorts = Dependencies.BUFFER_SIZE_BYTES_DEFAULT.bytesToSamples()
        repeat(bufferCount) {
            outputBuffers!!.add(ShortArray(bufferSizeShorts))
        }
    }

    private suspend fun playTrack() {
        var error: String?
        var track: Track? = null
        var buffersCreated = 0

        var sampleRate: Int? = null

        do {
            val trackId = newTrackId
            if (trackId != null) {
                val newTrack = getTrackToPlay(trackId)
                if (newTrack == null) {
                    emitError("Failed to load track.")
                    return
                }

                track = newTrack

                bufferFlow.emit(GeneratorEvent.Loading)

                loadTrack(newTrack)

                sampleRate = getEmulatorSampleRate()

                setupBuffers(sampleRate)

                error = getLastError()
                if (error != null) {
                    break
                }
                println("New track setup complete!")
            }

            if (sampleRate == null) {
                error = "Invalid sample rate."
                break
            }

            // Setup buffers.
            val bufferStartFrame = framesPlayed

            // Generate the next buffer of audio..
            val generatedAudio = outputBuffers!!.poll(TIMEOUT_BUFFERS_FULL_MS, TimeUnit.MILLISECONDS)
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

            FadeProcessor.fadeIfNecessary(
                    generatedAudio,
                    sampleRate,
                    bufferStartFrame.framesToMillis(sampleRate),
                    track!!.trackLengthMs - LENGTH_FADE_MILLIS,
                    LENGTH_FADE_MILLIS
            )

            // Emit this buffer.
            bufferFlow.tryEmit(
                    GeneratorEvent.Audio(
                            buffersCreated++,
                            bufferStartFrame.framesToMillis(sampleRate),
                            framesPlayed,
                            sampleRate,
                            generatedAudio
                    )
            )

            // Check if this coroutine has been cancelled.
            yield()
        } while (!isTrackOver())

        // Report error, if it happened.
        if (error != null) {
            emitError(error)
            return
        }

        teardown()

        // If no error, report completion.
        println("Playback complete!")
        bufferFlow.emit(GeneratorEvent.Complete)

        ongoingGenerationJob = null
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