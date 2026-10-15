package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiReportTest {
    @Test
    fun `percentile interpolates between the two bracketing samples`() {
        val sorted = listOf(10.0, 20.0, 30.0, 40.0)

        assertEquals(25.0, percentile(sorted, 50))
        assertEquals(37.0, percentile(sorted, 90))
        assertEquals(39.7, percentile(sorted, 99), 1e-9)
    }

    @Test
    fun `jank percent is the share of frames that missed their deadline`() {
        assertEquals(50.0, jankPercent(listOf(1.0, -2.0, 0.0, 3.0)))
        assertEquals(0.0, jankPercent(listOf(-1.0, -2.0)))
        assertEquals(100.0, jankPercent(listOf(0.5)))
    }

    @Test
    fun `ui result reports frame percentiles, frame count, jank, and trace sums`() {
        val measurement = UiMeasurement(
            scenario = "ui.browse_by_game_scroll",
            frameCpuMillis = listOf(10.0, 20.0, 30.0, 40.0),
            frameOverrunMillis = listOf(1.0, -2.0, 0.0, 3.0),
            traceSections = listOf(
                TraceSectionMeasurement(name = "GridImage", sumMs = 12.5, count = 7),
                TraceSectionMeasurement(name = "CrossfadeImage", sumMs = 8.0, count = 7),
            ),
        )

        val result = uiResult(measurement)

        assertEquals("ui.browse_by_game_scroll", result.scenario)
        assertEquals(4, result.frames)
        assertEquals(25.0, result.frameP50Ms)
        assertEquals(37.0, result.frameP90Ms)
        assertEquals(39.7, result.frameP99Ms, 1e-9)
        assertEquals(50.0, result.jankPercent)
        assertEquals(
            listOf(
                TraceSectionSum(name = "GridImage", sumMs = 12.5, count = 7),
                TraceSectionSum(name = "CrossfadeImage", sumMs = 8.0, count = 7),
            ),
            result.traceSections,
        )
    }

    @Test
    fun `jank percent is null when the device captured no overrun samples`() {
        val result = uiResult(
            UiMeasurement(
                scenario = "ui.browse_by_game_scroll",
                frameCpuMillis = listOf(5.0),
                frameOverrunMillis = emptyList(),
            ),
        )

        assertNull(result.jankPercent)
    }

    @Test
    fun `ui scenario ids are stable and resolvable`() {
        assertEquals(
            listOf(
                "ui.browse_by_game_scroll",
                "ui.home_scroll",
                "ui.browse_all_tracks_scroll",
                "ui.artist_detail_scroll",
            ),
            UiScenario.all.map { it.id },
        )
        assertEquals(UiScenario.BROWSE_BY_GAME_SCROLL, UiScenario.fromId("ui.browse_by_game_scroll"))
        assertEquals(UiScenario.HOME_SCROLL, UiScenario.fromId("ui.home_scroll"))
        assertEquals(UiScenario.BROWSE_ALL_TRACKS_SCROLL, UiScenario.fromId("ui.browse_all_tracks_scroll"))
        assertEquals(UiScenario.ARTIST_DETAIL_SCROLL, UiScenario.fromId("ui.artist_detail_scroll"))
        assertEquals(null, UiScenario.fromId("audio.usf"))
    }
}
