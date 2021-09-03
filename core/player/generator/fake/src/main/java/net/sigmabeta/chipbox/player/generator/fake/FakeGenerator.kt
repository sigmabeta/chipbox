package net.sigmabeta.chipbox.player.generator.fake

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.player.common.BYTES_PER_SAMPLE
import net.sigmabeta.chipbox.player.common.GeneratorEvent
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.fake.models.GeneratedTrack

class FakeGenerator(
    private val sampleRate: Int,
    private val bufferSizeBytes: Int,
    private val trackRandomizer: TrackRandomizer,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Generator {
    private lateinit var emulator: FakeEmulator

    private val generatorScope = CoroutineScope(dispatcher)

    private var ongoingGenerationJob: Job? = null

    private val trackFadeLengthMillis = 6_000.0

    private var framesPlayed = 0

    private lateinit var track: GeneratedTrack

    private val bufferFlow = MutableSharedFlow<GeneratorEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )

    override fun audioStream(trackId: Long): Flow<GeneratorEvent> {
        if (ongoingGenerationJob != null) {
            ongoingGenerationJob?.cancel()
        }

        ongoingGenerationJob = generatorScope.launch {
            // Emit loading status

            // Load track
            track = trackRandomizer.generate(trackId)
                ?: throw IllegalArgumentException("Could not load track with id $trackId")

            emulator = FakeEmulator(
                track,
                sampleRate
            )

            // Play made-up audio instead of it
            playTrack()
            ongoingGenerationJob = null
        }

        return bufferFlow.asSharedFlow()
    }

    private suspend fun playTrack() {
        var error: String? = null

        var buffersCreated = 0

        while (!emulator.trackOver) {
            // Check if this coroutine has been cancelled.
            yield()

            // TODO We should have a pool of buffers we use instead of a new one evrytiem
            // Setup buffers.
            val buffer = createBuffer()
            val bufferStartFrame = framesPlayed

            // Generate the next buffer of audio..
            framesPlayed += emulator.generateBuffer(buffer)

            // TODO This only reports errors if the *first* buffer generation fails. Do better!
//            if (framesPlayed == 0) {
//                error = "Failed to generate any audio."
//                break
//            }

            FadeProcessor.fadeIfNecessary(
                buffer,
                sampleRate,
                bufferStartFrame.framesToMillis(sampleRate),
                track.trackLengthMs - trackFadeLengthMillis,
                trackFadeLengthMillis
            )

            // TODO This should block if generator is too far ahead of speaker.
            // Emit this buffer.
            bufferFlow.emit(
                GeneratorEvent.Audio(
                    buffersCreated++,
                    bufferStartFrame.framesToMillis(sampleRate),
                    framesPlayed,
                    sampleRate,
                    buffer
                )
            )
        }

        // Report error, if it happened.
        if (error != null) {
            bufferFlow.emit(GeneratorEvent.Error)
            return
        }

        // If no error, report completion.
        bufferFlow.emit(GeneratorEvent.Complete)
    }

    private fun createBuffer(): ShortArray {
        val samplesPerBuffer = bufferSizeBytes / BYTES_PER_SAMPLE
        return ShortArray(samplesPerBuffer)
    }
}