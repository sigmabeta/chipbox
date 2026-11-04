package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals

class CacheReportTest {
    private fun phase(
        wall: Double,
        ttff: Double,
        loads: Int,
        teardowns: Int,
        source: String,
        sampleRate: Int = 44_100,
    ) = CachePhase(
        wallSeconds = wall,
        firstFrameSeconds = ttff,
        emulatorLoads = loads,
        emulatorTeardowns = teardowns,
        sourceClass = source,
        sampleRate = sampleRate,
    )

    private val measurement = CacheMeasurement(
        path = "Game/track.mini2sf",
        frames = 1_323_000,
        sampleRate = 44_100,
        cold = phase(1.20, 0.080, loads = 1, teardowns = 1, source = "CachingPcmSource"),
        warm = listOf(
            phase(0.10, 0.020, loads = 1, teardowns = 1, source = "CachedFilePcmSource"),
            phase(0.14, 0.024, loads = 1, teardowns = 1, source = "CachedFilePcmSource"),
            phase(0.12, 0.022, loads = 1, teardowns = 1, source = "CachedFilePcmSource"),
        ),
    )

    @Test
    fun `reduces warm opens to median wall and first-frame time`() {
        val row = cacheRow(measurement)

        assertEquals(0.12, row.warmWallMedianSeconds, 1e-9)
        assertEquals(0.10, row.warmWallMinSeconds, 1e-9)
        assertEquals(0.14, row.warmWallMaxSeconds, 1e-9)
        assertEquals(0.022, row.warmFirstFrameMedianSeconds, 1e-9)
    }

    @Test
    fun `reports per-open emulator construction and teardown counts`() {
        val row = cacheRow(measurement)

        assertEquals(1.0, row.warmEmulatorLoadsPerOpen, 1e-9)
        assertEquals(1.0, row.warmEmulatorTeardownsPerOpen, 1e-9)
        assertEquals("CachedFilePcmSource", row.warmSourceClass)
    }

    @Test
    fun `cold and warm classes identify which production path ran`() {
        val row = cacheRow(measurement)

        assertEquals("CachingPcmSource", row.coldSourceClass)
        assertEquals(1.0, row.coldEmulatorLoads, 1e-9)
        assertEquals(1.0, row.coldEmulatorTeardowns, 1e-9)
    }

    @Test
    fun `reports the source's sample rate rather than a backend constant`() {
        val row = cacheRow(
            measurement.copy(
                sampleRate = 32_000,
                cold = phase(1.0, 0.1, 1, 1, "CachingPcmSource", sampleRate = 32_000),
            ),
        )

        assertEquals(32_000, row.sampleRate)
    }

    @Test
    fun `reduces repeated uncached opens to median, min, max and per-open native counts`() {
        val row = uncachedRow(
            path = "Game/track.mini2sf",
            phases = listOf(
                phase(1.50, 0.090, loads = 1, teardowns = 1, source = "EmulatorPcmSource"),
                phase(1.30, 0.080, loads = 1, teardowns = 1, source = "EmulatorPcmSource"),
                phase(1.70, 0.100, loads = 1, teardowns = 1, source = "EmulatorPcmSource"),
            ),
            frames = 1_323_000,
        )

        assertEquals(3, row.opens)
        assertEquals(1.50, row.wallMedianSeconds, 1e-9)
        assertEquals(1.30, row.wallMinSeconds, 1e-9)
        assertEquals(1.70, row.wallMaxSeconds, 1e-9)
        assertEquals(0.090, row.firstFrameMedianSeconds, 1e-9)
        assertEquals(1.0, row.loadsPerOpen, 1e-9)
        assertEquals(1.0, row.teardownsPerOpen, 1e-9)
        assertEquals("EmulatorPcmSource", row.sourceClass)
        assertEquals(44_100, row.sampleRate)
    }
}
