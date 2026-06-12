package net.sigmabeta.chipbox.player.common

/**
 * 4-point Catmull-Rom cubic [Resampler]. A small quality step up from [LinearResampler] — it fits a
 * cubic through the two samples bracketing the read position plus one neighbour on each side, which
 * preserves more high-frequency detail with only a few extra multiplies per output sample.
 *
 * Reads `sm1 = sample(i - 1)`, `s0 = sample(i)`, `s1 = sample(i + 1)`, `s2 = sample(i + 2)` and
 * evaluates the Catmull-Rom basis at [frac].
 */
class CubicResampler(inputRate: Int, outputRate: Int) : StreamingResampler(inputRate, outputRate, taps = 4, leftTaps = 1) {

    override fun kernel(input: ShortArray, i: Int, frac: Double, channel: Int): Double {
        val sm1 = sample(input, i - 1, channel)
        val s0 = sample(input, i, channel)
        val s1 = sample(input, i + 1, channel)
        val s2 = sample(input, i + 2, channel)

        // Catmull-Rom: f(t) = ((a*t + b)*t + c)*t + d, evaluated via Horner.
        val a = -HALF * sm1 + THREE_HALVES * s0 - THREE_HALVES * s1 + HALF * s2
        val b = sm1 - FIVE_HALVES * s0 + 2.0 * s1 - HALF * s2
        val c = -HALF * sm1 + HALF * s1
        val d = s0.toDouble()
        return ((a * frac + b) * frac + c) * frac + d
    }

    private companion object {
        const val HALF = 0.5
        const val THREE_HALVES = 1.5
        const val FIVE_HALVES = 2.5
    }
}
