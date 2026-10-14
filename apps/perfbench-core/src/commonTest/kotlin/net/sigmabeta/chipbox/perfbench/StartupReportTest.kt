package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StartupReportTest {
    @Test
    fun `startup result reports time-to-first-content percentiles and the samples`() {
        val measurement = StartupMeasurement(
            scenario = "startup.cold",
            timeToFirstContentMillis = listOf(10.0, 20.0, 30.0, 40.0),
        )

        val result = startupResult(measurement)

        assertEquals("startup.cold", result.scenario)
        assertEquals(25.0, result.timeToFirstContentP50Ms)
        assertEquals(37.0, result.timeToFirstContentP90Ms)
        assertEquals(39.7, result.timeToFirstContentP99Ms, 1e-9)
        assertEquals(listOf(10.0, 20.0, 30.0, 40.0), result.samplesMs)
    }

    @Test
    fun `startup result of a single sample reports that sample at every percentile`() {
        val result = startupResult(
            StartupMeasurement(
                scenario = "startup.warm",
                timeToFirstContentMillis = listOf(123.4),
            ),
        )

        assertEquals(123.4, result.timeToFirstContentP50Ms)
        assertEquals(123.4, result.timeToFirstContentP90Ms)
        assertEquals(123.4, result.timeToFirstContentP99Ms)
    }

    @Test
    fun `startup result requires at least one sample`() {
        assertFailsWith<IllegalArgumentException> {
            startupResult(StartupMeasurement(scenario = "startup.cold", timeToFirstContentMillis = emptyList()))
        }
    }

    @Test
    fun `startup scenario ids are stable and resolvable`() {
        assertEquals(
            listOf("startup.cold", "startup.warm"),
            StartupScenario.all.map { it.id },
        )
        assertEquals(StartupScenario.COLD, StartupScenario.fromId("startup.cold"))
        assertEquals(StartupScenario.WARM, StartupScenario.fromId("startup.warm"))
        assertEquals(null, StartupScenario.fromId("ui.browse_by_game_scroll"))
    }
}
