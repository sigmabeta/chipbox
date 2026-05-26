package net.sigmabeta.chipbox.player.common

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NormalizationTest {

    @Test
    fun `non-finite loudness yields unity gain`() {
        // First-time renders haven't measured a 400 ms block yet — gain stays 1.0 so we don't
        // panic-scale by an undefined factor.
        assertEquals(1.0, normalizationGain(Double.NaN, truePeakDbtp = -3.0))
        assertEquals(1.0, normalizationGain(Double.POSITIVE_INFINITY, truePeakDbtp = -3.0))
        assertEquals(1.0, normalizationGain(Double.NEGATIVE_INFINITY, truePeakDbtp = -3.0))
    }

    @Test
    fun `positive LUFS readings are treated as corrupt and yield unity gain`() {
        // LUFS is always <= 0 in a sane measurement — a positive value points at a bad header
        // field, not a real signal we should attenuate based on.
        assertEquals(1.0, normalizationGain(loudnessLufs = 3.0, truePeakDbtp = -3.0))
    }

    @Test
    fun `at-target loudness with infinite headroom gives unity gain`() {
        // -14 LUFS in, target -14 LUFS — nothing to do.
        assertCloseTo(expected = 1.0, actual = normalizationGain(-14.0, truePeakDbtp = Double.NaN))
    }

    @Test
    fun `quiet tracks get boosted toward the target`() {
        // -24 LUFS is 10 dB under target; amplitude gain is 10^(10/20) ≈ 3.162.
        val expected = 10.0.pow(0.5)
        assertCloseTo(expected, normalizationGain(loudnessLufs = -24.0, truePeakDbtp = Double.NaN))
    }

    @Test
    fun `loud tracks get attenuated toward the target`() {
        // -10 LUFS is 4 dB over target; amplitude factor is 10^(-4/20) ≈ 0.631.
        val expected = 10.0.pow(-4.0 / 20.0)
        assertCloseTo(expected, normalizationGain(loudnessLufs = -10.0, truePeakDbtp = Double.NaN))
    }

    @Test
    fun `peak ceiling clamps the boost when headroom would clip`() {
        // -24 LUFS would ask for ~3.162x; but a true peak of -10 dBTP only leaves ~2.818x of
        // headroom under the -1 dBTP ceiling. Peak cap wins.
        val peakCap = 10.0.pow((-1.0 - -10.0) / 20.0)
        val gain = normalizationGain(loudnessLufs = -24.0, truePeakDbtp = -10.0)
        assertCloseTo(peakCap, gain)
        assertTrue(gain < 10.0.pow(0.5), "peak cap must beat loudness gain when headroom is tight")
    }

    @Test
    fun `peak ceiling stays out of the way when headroom is generous`() {
        // -20 dBTP leaves ~8.9x of headroom — well above the loudness ask of ~3.162x — so the
        // loudness gain wins and the peak cap doesn't matter.
        val expected = 10.0.pow(0.5)
        assertCloseTo(expected, normalizationGain(loudnessLufs = -24.0, truePeakDbtp = -20.0))
    }

    @Test
    fun `non-finite true peak removes the ceiling entirely`() {
        // Same loudness with measured peak vs no peak should agree as long as the peak doesn't
        // actually cap — verifies the +inf branch isn't accidentally min()ing to zero.
        val noPeak = normalizationGain(loudnessLufs = -24.0, truePeakDbtp = Double.NaN)
        val plentyOfHeadroom = normalizationGain(loudnessLufs = -24.0, truePeakDbtp = -40.0)
        assertCloseTo(noPeak, plentyOfHeadroom)
    }

    private fun assertCloseTo(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(
            abs(expected - actual) < tolerance,
            "expected $expected, got $actual (delta ${abs(expected - actual)})",
        )
    }
}
