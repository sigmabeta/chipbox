package net.sigmabeta.chipbox.player.generator.fake

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import kotlin.math.PI
import kotlin.math.sin

class FakeGenerator(
    private val speaker: Speaker,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Generator {
    private val generatorScope = CoroutineScope(dispatcher)

    private var ongoingGenerationJob: Job? = null

    private val amplitude = 0.75f

    private val trackLengthMs = 10L

    private val sampleRate = 48_000 // aka 48 samples per msec

    private val framesPerMillis = sampleRate / MILLIS_PER_SECOND

    private val framesPerBuffer = 1L.millisToFrames()

    private val trackLengthFrames = trackLengthMs.millisToFrames()

    private val sinFrequency = 440.0f / MILLIS_PER_SECOND

    override suspend fun play(trackId: Long) {
        ongoingGenerationJob?.cancelAndJoin()
        ongoingGenerationJob = generatorScope.launch {
            // Emit loading status

            // Load track

            // Play made-up audio instead of it
            playTrack(/*track*/)
        }
    }

    private suspend fun playTrack(/*track: Track*/) {
        var error: String? = null

        var framesPlayed = 0L
        var remainingFrames = trackLengthFrames - framesPlayed

        // Setup buffers.
        val buffer = ShortArray(framesPerBuffer.framesToShorts())

        while (remainingFrames > 0) {
            // Check if this coroutine has been cancelled.
            yield()

            // Generate $remainingSamples worth of audio.
            for (currentFrame in 0 until framesPerBuffer) {
                if (remainingFrames <= 0) {
                    for (sampleOffset in 0 until SHORTS_PER_FRAME) {
                        buffer[(currentFrame.framesToShorts() + sampleOffset)] = 0
                    }
                    continue
                }

                val currentMillis = framesPlayed.framesToMillis()
                val sineValueForFrame = sineValueForFrame(currentMillis)

                for (sampleOffset in 0 until SHORTS_PER_FRAME) {
                    buffer[(currentFrame.framesToShorts() + sampleOffset)] = sineValueForFrame
                }

                framesPlayed++
                remainingFrames = trackLengthFrames - framesPlayed
            }

            // TODO This should block if generator is too far ahead of speaker.
            // Give those samples to the speaker.
            speaker.play(buffer)
        }

        if (error != null) {
            // Emit Failed status
        }

        // Emit Finished status
    }

    private fun sineValueForFrame(timeMillis: Double): Short {
        return getSineValue(timeMillis)
            .scaleByAmplitude(amplitude)
            .toShortValue()
    }

    private fun getSineValue(timeMillis: Double): Double {
        val scalar = 2 * PI
        return sin(scalar * sinFrequency * timeMillis)
    }

    private fun Long.millisToFrames() = this * framesPerMillis

    private fun Long.framesToMillis() = this / framesPerMillis.toDouble()

    private fun Long.framesToShorts() = (this * SHORTS_PER_FRAME).toInt()

    private fun Double.toShortValue() = this.toInt().toShort()

    private fun Double.scaleByAmplitude(amplitude: Float) = this * amplitude * Short.MAX_VALUE

    companion object {
        private const val MILLIS_PER_SECOND = 1_000
        private const val CHANNELS_STEREO = 2
        private const val BYTES_PER_SHORT = 2
        private const val BYTES_PER_SAMPLE = BYTES_PER_SHORT
        private const val BYTES_PER_FRAME = BYTES_PER_SAMPLE * CHANNELS_STEREO
        private const val SHORTS_PER_FRAME = BYTES_PER_FRAME / BYTES_PER_SAMPLE
    }
}