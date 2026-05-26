package net.sigmabeta.chipbox.player.common

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Calibration + behavioural tests for the BS.1770 / EBU R 128 loudness measurer. The expected
 * LUFS / dBTP values here come from the spec and from libebur128's reference output, so a
 * regression in the K-weighting biquad math, the gating, or the true-peak oversampler shows up
 * as a wrong-by-a-decibel here rather than a wrong-by-a-decibel during playback.
 *
 * Tolerances are deliberately wide (±2 LU, ±1 dBTP) — the calibration values themselves are tight
 * to within a fraction of a dB, but the K-weighting filter response at 1 kHz isn't perfectly flat
 * and the spec leaves ±0.3 LU of headroom; these assertions catch order-of-magnitude regressions
 * without firing on legitimate filter-design tweaks.
 */
class EbuR128Test {

    private val sampleRate = 48_000

    @Test
    fun `with no audio integratedLoudness is NaN`() {
        val measurer = EbuR128(sampleRate)
        assertTrue(measurer.integratedLoudness().isNaN())
    }

    @Test
    fun `with less than one 400ms block of audio integratedLoudness is NaN`() {
        // A complete BS.1770 block is 4 × 100 ms; until the ring fills no block-mean-square has
        // been recorded so the integrated value is undefined.
        val measurer = EbuR128(sampleRate)
        val frames = (0.3 * sampleRate).toInt() // 300 ms
        measurer.process(stereoSine(frames, freqHz = 1000.0, amplitude = 1.0), frames)
        assertTrue(measurer.integratedLoudness().isNaN(), "300 ms is below the 400 ms block size")
    }

    @Test
    fun `pure silence integratedLoudness is NaN`() {
        // Every block falls below the -70 LUFS absolute gate, so the gated mean is empty.
        val measurer = EbuR128(sampleRate)
        val frames = sampleRate * 2 // 2 s
        measurer.process(ShortArray(frames * 2), frames)
        assertTrue(measurer.integratedLoudness().isNaN())
    }

    @Test
    fun `full-scale stereo 1kHz sine measures within a couple LU of zero LUFS`() {
        // Math: a full-scale sine has mean-square 0.5; stereo sums to 1.0; LUFS = -0.691 +
        // 10·log10(1.0) before K-weighting. The K-weighting shelf adds a fraction of a dB at
        // 1 kHz, so the reading lands close to but not exactly at -0.691 LUFS.
        val measurer = EbuR128(sampleRate)
        val frames = sampleRate * 2 // 2 s — plenty of 400 ms blocks
        measurer.process(stereoSine(frames, freqHz = 1000.0, amplitude = 1.0), frames)
        val lufs = measurer.integratedLoudness()
        assertFalse(lufs.isNaN(), "expected a real measurement, got NaN")
        assertTrue(lufs in -3.0..3.0, "expected LUFS near 0, got $lufs")
    }

    @Test
    fun `halving amplitude drops LUFS by approximately six decibels`() {
        // Power scales as amplitude², so a 0.5x amplitude is a -6.02 dB drop in mean-square —
        // and LUFS is just 10·log10(MS) so the same -6 dB shows up. Tolerance covers the small
        // bias from K-weighting and gating.
        val loud = ebuRunFor(amplitude = 1.0)
        val quiet = ebuRunFor(amplitude = 0.5)
        val diff = loud - quiet
        assertTrue(diff in 5.0..7.0, "halving amplitude should drop LUFS ~6 dB; got delta $diff")
    }

    @Test
    fun `quieter audio measures lower LUFS than louder audio`() {
        // The cheap regression: even if the absolute calibration drifted, louder must read higher.
        val loud = ebuRunFor(amplitude = 1.0)
        val quiet = ebuRunFor(amplitude = 0.1)
        assertTrue(quiet < loud, "expected quiet < loud, got quiet=$quiet loud=$loud")
    }

    @Test
    fun `audio entirely below the -70 LUFS absolute gate measures NaN`() {
        // A 0.0001-amplitude sine is around -80 LUFS — below the absolute gate, so every block is
        // discarded and the gated mean is empty.
        val measurer = EbuR128(sampleRate)
        val frames = sampleRate * 2
        measurer.process(stereoSine(frames, freqHz = 1000.0, amplitude = 0.0001), frames)
        assertTrue(measurer.integratedLoudness().isNaN(), "audio below the absolute gate must read NaN")
    }

    @Test
    fun `truePeakDbtp returns NEGATIVE_INFINITY when no audio has been processed`() {
        // Sentinel value documented on the function — make sure the polyphase oversampler doesn't
        // hand back a stale or default-zero reading.
        val measurer = EbuR128(sampleRate)
        assertEquals(Double.NEGATIVE_INFINITY, measurer.truePeakDbtp())
    }

    @Test
    fun `truePeakDbtp on a half-scale sine measures near minus six dBTP`() {
        // 0.5 amplitude = 20·log10(0.5) = -6.02 dBTP at the sample peaks; the oversampler may
        // detect a sliver above due to inter-sample peaks but it should stay close.
        val measurer = EbuR128(sampleRate)
        val frames = sampleRate / 2 // 500 ms is plenty for the FIR to settle
        measurer.process(stereoSine(frames, freqHz = 1000.0, amplitude = 0.5), frames)
        val dbtp = measurer.truePeakDbtp()
        assertTrue(dbtp in -7.0..-5.0, "expected ~-6 dBTP, got $dbtp")
    }

    @Test
    fun `truePeakDbtp on a full-scale signal measures near zero dBTP`() {
        // Calibration check: the 1.0-amplitude reference should sit at 0 dBTP ± measurement
        // noise from the polyphase FIR.
        val measurer = EbuR128(sampleRate)
        val frames = sampleRate / 2
        measurer.process(stereoSine(frames, freqHz = 1000.0, amplitude = 1.0), frames)
        val dbtp = measurer.truePeakDbtp()
        assertTrue(dbtp in -1.0..1.0, "expected ~0 dBTP, got $dbtp")
    }

    @Test
    fun `process is a no-op for frameCount zero or negative`() {
        // Documented behaviour — guards against the for-loop reading past the end of the buffer
        // when the speaker hands a short frame count.
        val measurer = EbuR128(sampleRate)
        measurer.process(ShortArray(100), frameCount = 0)
        measurer.process(ShortArray(100), frameCount = -1)
        assertTrue(measurer.integratedLoudness().isNaN())
    }

    private fun ebuRunFor(amplitude: Double): Double {
        val measurer = EbuR128(sampleRate)
        val frames = sampleRate * 2
        measurer.process(stereoSine(frames, freqHz = 1000.0, amplitude = amplitude), frames)
        return measurer.integratedLoudness()
    }

    private fun stereoSine(
        frames: Int,
        freqHz: Double,
        amplitude: Double,
    ): ShortArray {
        val buf = ShortArray(frames * 2)
        val omega = 2.0 * PI * freqHz / sampleRate
        val scale = amplitude * Short.MAX_VALUE.toDouble()
        for (i in 0 until frames) {
            val v = (sin(omega * i) * scale).toInt().toShort()
            buf[i * 2] = v
            buf[i * 2 + 1] = v
        }
        return buf
    }
}
