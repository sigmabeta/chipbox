package net.sigmabeta.chipbox.player.common

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [TruePeak] to the straightforward modulo-indexed per-sample walk ([ModuloTruePeak]). The
 * buffer-wise window and the skip of chunks that cannot raise the peak exist only for speed, so any
 * drift from the reference, even one ulp, is a bug: the PCM cache header stores this peak and it
 * must not change between builds.
 */
class TruePeakTest {

    @Test
    fun `buffer-wise peak with skipping is bit-identical to the modulo reference`() {
        val random = Random(SEED)
        repeat(TRIALS) { trial ->
            val channels = 1 + trial % 2
            val truePeak = TruePeak(channels)
            val reference = ModuloTruePeak(channels, truePeak.coefficients)
            val frames = MIN_FRAMES + random.nextInt(MAX_FRAMES - MIN_FRAMES)
            val pcm = segmentedSignal(random, frames, channels, SAMPLE_RATE)
            val channelBuffer = DoubleArray(MAX_BUFFER_FRAMES)
            var f = 0
            while (f < frames) {
                // Sizes below the 11-sample history, around the skip-chunk size, and well past it.
                val count = minOf(frames - f, 1 + random.nextInt(MAX_BUFFER_FRAMES))
                for (c in 0 until channels) {
                    for (i in 0 until count) {
                        val sample = pcm[(f + i) * channels + c] / Short.MAX_VALUE.toDouble()
                        channelBuffer[i] = sample
                        reference.process(c, sample)
                    }
                    truePeak.process(c, channelBuffer, count)
                }
                f += count
                assertEquals(reference.maxAbs.toRawBits(), truePeak.maxAbs.toRawBits(), "trial $trial, frame $f")
            }
        }
    }

    /**
     * The oversampler's output lags its input by about half the filter length, so a burst's
     * inter-sample peak is computed while reading the samples *after* it. When those are silence,
     * only the carried history in the skip bound stops the chunk from being skipped. An fs/4 tone
     * at 45° (samples +A, +A, -A, -A) peaks about √2·A between samples, well above its raw peak.
     */
    @Test
    fun `an inter-sample peak landing in silence after the burst is still measured`() {
        val burst = doubleArrayOf(BURST, BURST, -BURST, -BURST, BURST, BURST, -BURST, -BURST)
        for (offset in 0..SWEEP_FRAMES - burst.size) {
            // Within one buffer, so the burst's tail meets every chunk boundary position.
            val samples = DoubleArray(SWEEP_FRAMES)
            burst.copyInto(samples, offset)
            assertMatchesReference(listOf(samples), "burst at $offset")
            // Across buffers: the burst ends one buffer, the next buffer is all silence.
            assertMatchesReference(
                listOf(samples.copyOf(offset + burst.size), DoubleArray(TAIL_FRAMES)),
                "burst ending a buffer at $offset",
            )
        }
    }

    private fun assertMatchesReference(buffers: List<DoubleArray>, where: String) {
        val truePeak = TruePeak(1)
        val reference = ModuloTruePeak(1, truePeak.coefficients)
        for (buffer in buffers) {
            truePeak.process(0, buffer, buffer.size)
            for (sample in buffer) reference.process(0, sample)
        }
        assertEquals(reference.maxAbs.toRawBits(), truePeak.maxAbs.toRawBits(), where)
    }

    private companion object {
        const val BURST = 0.5
        const val SWEEP_FRAMES = 700
        const val TAIL_FRAMES = 64
        const val SEED = 0x7E1E
        const val TRIALS = 24
        const val SAMPLE_RATE = 44_100
        const val MIN_FRAMES = 2_000
        const val MAX_FRAMES = 60_000
        const val MAX_BUFFER_FRAMES = 3_000
    }
}
