package net.sigmabeta.chipbox.player.resampler

/**
 * 2-point linear-interpolation [Resampler]. Cheapest kernel; the web audio worklet has used it for
 * chiptune for the same reason it suffices here — the source is already band-limited by the
 * emulator's own DAC model and the ratios in play (~32k/44.1k → 48k) are mild.
 *
 * Reads `s0 = sample(i)`, `s1 = sample(i + 1)`; output = `s0 + (s1 - s0) * frac`.
 */
class LinearResampler : StreamingResampler(taps = 2, leftTaps = 0) {

    override fun kernel(input: ShortArray, i: Int, frac: Double, channel: Int): Double {
        val s0 = sample(input, i, channel)
        val s1 = sample(input, i + 1, channel)
        return s0 + (s1 - s0) * frac
    }
}
