package net.sigmabeta.chipbox.abrender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The golden check's tolerance rule. [GoldenTolerance.STRICT] (the host default) tolerates nothing;
 * [GoldenTolerance.CROSS_ISA] (the device default) accepts the ±1 LSB a floating-point resampler
 * drifts across architectures, while every real divergence — a larger delta, a missing/extra track,
 * or an error — stays a failure.
 */
class GoldenReportToleranceTest {
    private fun changed(maxDelta: Int?, diffRmsDbfs: Double): DiffRow =
        DiffRow("Game/track", "Game", "Title", Verdict.CHANGED, 0.0, diffRmsDbfs, "maxΔ=$maxDelta", maxDelta)

    @Test
    fun `strict rejects a one-LSB change`() {
        val report = GoldenReport(listOf(changed(maxDelta = 1, diffRmsDbfs = -104.19)))
        assertFalse(report.passed)
        assertEquals(1, report.failures.size)
        assertTrue(report.tolerated.isEmpty())
        assertTrue(report.describe().contains("diverged"))
    }

    @Test
    fun `cross-ISA accepts a one-LSB change`() {
        val report = GoldenReport(listOf(changed(maxDelta = 1, diffRmsDbfs = -104.19)), GoldenTolerance.CROSS_ISA)
        assertTrue(report.passed)
        assertEquals(1, report.tolerated.size)
        assertTrue(report.failures.isEmpty())
        assertTrue(report.describe().contains("within tolerance"))
    }

    @Test
    fun `cross-ISA rejects a two-LSB change`() {
        val report = GoldenReport(listOf(changed(maxDelta = 2, diffRmsDbfs = -95.0)), GoldenTolerance.CROSS_ISA)
        assertFalse(report.passed)
        assertEquals(1, report.failures.size)
    }

    @Test
    fun `cross-ISA rejects a change above the RMS floor even at one LSB`() {
        val report = GoldenReport(listOf(changed(maxDelta = 1, diffRmsDbfs = -40.0)), GoldenTolerance.CROSS_ISA)
        assertFalse(report.passed)
    }

    @Test
    fun `cross-ISA still rejects missing tracks, extras, and errors`() {
        val rows = listOf(
            DiffRow("Game/missing", "Game", "Missing", Verdict.ONLY_IN_A, Double.NaN, Double.NaN, ""),
            DiffRow("Game/extra", "Game", "Extra", Verdict.ONLY_IN_B, Double.NaN, Double.NaN, ""),
            DiffRow("Game/broken", "Game", "Broken", Verdict.STILL_ERROR, Double.NaN, Double.NaN, "boom"),
        )
        val report = GoldenReport(rows, GoldenTolerance.CROSS_ISA)
        assertFalse(report.passed)
        assertEquals(3, report.failures.size)
        assertTrue(report.tolerated.isEmpty())
    }

    @Test
    fun `identical rows are neither tolerated nor failures`() {
        val row = DiffRow("Game/track", "Game", "Title", Verdict.IDENTICAL, 0.0, Double.NEGATIVE_INFINITY, "", 0)
        val report = GoldenReport(listOf(row), GoldenTolerance.CROSS_ISA)
        assertTrue(report.passed)
        assertTrue(report.tolerated.isEmpty())
        assertTrue(report.failures.isEmpty())
        assertTrue(report.describe().contains("bit-identical"))
    }
}
