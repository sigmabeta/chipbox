package net.sigmabeta.chipbox.player.common

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [TruePeak] to the straightforward modulo-indexed polyphase walk it replaced. The mirrored
 * history exists only to drop the per-tap `%`, so any drift from the reference, even one ulp, is a
 * bug: the PCM cache header stores this peak and it must not change between builds.
 */
class TruePeakTest {

    @Test
    fun `mirrored history peak is bit-identical to the modulo reference`() {
        val random = Random(SEED)
        repeat(TRIALS) { trial ->
            val channels = 1 + trial % 2
            val truePeak = TruePeak(channels)
            val reference = ModuloTruePeak(channels, truePeak.coefficients)
            // Longer than several ring wraps, with a mix of quiet and full-scale samples.
            val frames = 1 + random.nextInt(MAX_FRAMES)
            val scale = if (trial % 3 == 0) 1.0 else random.nextDouble()
            for (f in 0 until frames) {
                for (c in 0 until channels) {
                    val sample = (random.nextDouble() * 2.0 - 1.0) * scale
                    truePeak.process(c, sample)
                    reference.process(c, sample)
                }
                assertEquals(reference.maxAbs, truePeak.maxAbs, "trial $trial, frame $f")
            }
        }
    }

    /** The pre-mirroring implementation, kept verbatim apart from taking the coefficients in. */
    private class ModuloTruePeak(channels: Int, private val coefficients: Array<DoubleArray>) {
        private val taps = coefficients[0].size
        private val history = Array(channels) { DoubleArray(taps) }
        private val historyPos = IntArray(channels)
        var maxAbs = 0.0

        fun process(channel: Int, sample: Double) {
            val a = abs(sample)
            if (a > maxAbs) maxAbs = a
            val hist = history[channel]
            val pos = historyPos[channel]
            hist[pos] = sample
            for (co in coefficients) {
                var sum = 0.0
                for (t in 0 until taps) {
                    sum += hist[((pos - t) + taps) % taps] * co[t]
                }
                val mag = abs(sum)
                if (mag > maxAbs) maxAbs = mag
            }
            historyPos[channel] = (pos + 1) % taps
        }
    }

    private companion object {
        const val SEED = 0x7E1E
        const val TRIALS = 24
        const val MAX_FRAMES = 2_000
    }
}
