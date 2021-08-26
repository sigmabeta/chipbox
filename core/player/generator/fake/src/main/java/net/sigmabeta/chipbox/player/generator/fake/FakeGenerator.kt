package net.sigmabeta.chipbox.player.generator.fake

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.player.common.*
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.fake.models.Note
import net.sigmabeta.chipbox.player.generator.fake.models.NoteRandomizer
import kotlin.math.PI
import kotlin.math.sin

class FakeGenerator(
    private val sampleRate: Int,
    bufferSizeBytes: Int,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Generator {
    private val generatorScope = CoroutineScope(dispatcher)

    private var ongoingGenerationJob: Job? = null

    private val samplesPerBuffer = bufferSizeBytes / 2

    private val framesPerBuffer = samplesPerBuffer / 2

    private val amplitude = 0.75f

    private lateinit var note: Note

    private val bufferFlow = MutableSharedFlow<GeneratorEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 10
    )

    override fun audioStream(trackId: Long): Flow<GeneratorEvent> {
        if (ongoingGenerationJob != null) {
            throw RuntimeException("Cannot run two generator coroutines at once.")
        }
        generatorScope.launch {
            // Emit loading status

            // Load track
            note = NoteRandomizer.randomNote(trackId)

            // Play made-up audio instead of it
            playTrack()
        }

        return bufferFlow.asSharedFlow()
    }

    private suspend fun playTrack() {
        var error: String? = null

        val trackLengthFrames = note.durationMillis.millisToFrames(sampleRate)

        var buffersCreated = 0
        var framesPlayed = 0L
        var remainingFrames = trackLengthFrames - framesPlayed

        while (remainingFrames > 0) {
            // Check if this coroutine has been cancelled.
            yield()

            // Setup buffers.
            val buffer = ShortArray(samplesPerBuffer)

            val bufferStartFrame = framesPlayed
            // Generate $remainingSamples worth of audio.
            for (currentFrame in 0 until framesPerBuffer) {
                if (remainingFrames <= 0) {
                    for (sampleOffset in 0 until SHORTS_PER_FRAME) {
                        buffer[(currentFrame.framesToShorts() + sampleOffset)] = 0
                    }
                    continue
                }

                val currentMillis = framesPlayed.framesToMillis(sampleRate)
                val sineValueForFrame = sineValueForFrame(currentMillis)

                for (sampleOffset in 0 until SHORTS_PER_FRAME) {
                    buffer[(currentFrame.framesToShorts() + sampleOffset)] = sineValueForFrame
                }

                framesPlayed++
                remainingFrames = trackLengthFrames - framesPlayed
            }

            buffersCreated++

            // TODO This should block if generator is too far ahead of speaker.
            // Emit this buffer.
            bufferFlow.emit(
                GeneratorEvent.Audio(
                    buffersCreated,
                    bufferStartFrame.framesToMillis(sampleRate),
                    framesPlayed,
                    sampleRate,
                    buffer
                )
            )
        }

        if (error != null) {
            bufferFlow.emit(GeneratorEvent.Error)
            return
        }

        bufferFlow.emit(GeneratorEvent.Complete)
    }

    private fun sineValueForFrame(timeMillis: Double): Short {
        return getSineValue(timeMillis)
            .scaleByAmplitude(amplitude)
            .toShortValue()
    }

    private fun getSineValue(timeMillis: Double): Double {
        val scalar = 2 * PI
        return sin(scalar * note.pitch.frequency.rateInMillis() * timeMillis)
    }

    private fun Double.scaleByAmplitude(amplitude: Float) = this * amplitude * Short.MAX_VALUE
}