package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioReportTest {
    @Test
    fun `median averages the middle pair for an even count`() {
        assertEquals(2.0, median(listOf(1.0, 2.0, 3.0)))
        assertEquals(2.5, median(listOf(4.0, 1.0, 2.0, 3.0)))
    }

    @Test
    fun `rtf is audio duration over the median wall time`() {
        val measurement = AudioMeasurement(
            path = "Chrono Trigger/track.spc",
            frames = 44_100L * 30,
            sampleRate = 44_100,
            wallSeconds = listOf(3.0, 2.0, 4.0),
            cpuSeconds = listOf(1.0, 1.4, 1.2),
        )

        val result = audioResult(measurement)

        assertEquals("Chrono Trigger/track.spc", result.path)
        assertEquals(30.0 / 3.0, result.rtf)
        assertEquals(3.0, result.median)
        assertEquals(2.0, result.min)
        assertEquals(4.0, result.max)
        assertEquals(listOf(3.0, 2.0, 4.0), result.samples)
        assertEquals(1.2, result.cpuSeconds)
        assertNull(result.error)
    }

    @Test
    fun `cpu time is reported separately from the wall-time rtf`() {
        val measurement = AudioMeasurement(
            path = "game/track.vgm",
            frames = 48_000L,
            sampleRate = 48_000,
            wallSeconds = listOf(2.0),
            cpuSeconds = listOf(0.5),
        )

        val result = audioResult(measurement)

        assertEquals(0.5, result.rtf)
        assertEquals(0.5, result.cpuSeconds)
    }

    @Test
    fun `cpu seconds are null when the JVM reported none`() {
        val measurement = AudioMeasurement(
            path = "game/track.psf",
            frames = 44_100L,
            sampleRate = 44_100,
            wallSeconds = listOf(1.0),
            cpuSeconds = emptyList(),
        )

        assertNull(audioResult(measurement).cpuSeconds)
    }

    @Test
    fun `errored audio result carries its error and no real-time factor`() {
        val result = erroredAudioResult("game/broken.spc", error = "no audio produced")

        assertEquals("game/broken.spc", result.path)
        assertEquals("no audio produced", result.error)
        assertNull(result.rtf)
        assertNull(result.median)
        assertNull(result.min)
        assertNull(result.max)
        assertNull(result.cpuSeconds)
        assertTrue(result.samples.isEmpty())
    }
}
