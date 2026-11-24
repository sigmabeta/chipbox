package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BaselineTest {
    @Test
    fun `takes each scenario's whole entry from the recording with the median frame p50`() {
        val recordings = listOf(
            run("2026-11-01", ui(scroll("a", p50 = 9.0, frames = 900), scroll("b", p50 = 5.0, frames = 500))),
            run("2026-11-02", ui(scroll("a", p50 = 7.0, frames = 700), scroll("b", p50 = 6.0, frames = 600))),
            run("2026-11-03", ui(scroll("a", p50 = 8.0, frames = 800), scroll("b", p50 = 4.0, frames = 400))),
        )

        val merged = assertIs<BaselineOutcome.Merged>(medianBaseline(recordings))

        val results = (merged.result.axes.getValue("ui") as UiAxis).results.associateBy { it.scenario }
        assertEquals(scroll("a", p50 = 8.0, frames = 800), results.getValue("a"))
        assertEquals(scroll("b", p50 = 5.0, frames = 500), results.getValue("b"))
    }

    @Test
    fun `keeps the newest recording's run-wide blocks`() {
        val recordings = listOf(
            run("2026-11-01", ui(scroll("a", p50 = 9.0))),
            run("2026-11-02", ui(scroll("a", p50 = 7.0))),
        )

        val merged = assertIs<BaselineOutcome.Merged>(medianBaseline(recordings))

        assertEquals("2026-11-02", merged.result.generated)
    }

    @Test
    fun `takes the lower median of an even count`() {
        val recordings = listOf(
            run("1", ui(scroll("a", p50 = 9.0))),
            run("2", ui(scroll("a", p50 = 7.0))),
        )

        val merged = assertIs<BaselineOutcome.Merged>(medianBaseline(recordings))

        assertEquals(7.0, (merged.result.axes.getValue("ui") as UiAxis).results.single().frameP50Ms)
        assertEquals(1, merged.spreads.single().chosen)
    }

    @Test
    fun `reports each scenario's values across the recordings`() {
        val recordings = listOf(
            run("1", ui(scroll("a", p50 = 9.0))),
            run("2", ui(scroll("a", p50 = 7.0))),
            run("3", ui(scroll("a", p50 = 8.0))),
        )

        val spread = assertIs<BaselineOutcome.Merged>(medianBaseline(recordings)).spreads.single()

        assertEquals("ui", spread.axis)
        assertEquals("a", spread.scenario)
        assertEquals(listOf(9.0, 7.0, 8.0), spread.values)
        assertEquals(2, spread.chosen)
    }

    @Test
    fun `refuses recordings with different scenario sets`() {
        val recordings = listOf(
            run("1", ui(scroll("a", p50 = 9.0))),
            run("2", ui(scroll("b", p50 = 7.0))),
        )

        val refused = assertIs<BaselineOutcome.Refused>(medianBaseline(recordings))

        assertIs<DiffRefusal.ScenarioSetMismatch>(refused.reason)
    }

    @Test
    fun `keeps an audio entry no recording could measure from the newest recording`() {
        val failed = AudioResult(path = "game/track.spc", error = "boom")
        val recordings = listOf(
            run("1", audio(failed.copy(error = "older"))),
            run("2", audio(failed)),
        )

        val merged = assertIs<BaselineOutcome.Merged>(medianBaseline(recordings))

        assertEquals(failed, (merged.result.axes.getValue("audio") as AudioAxis).results.single())
    }

    @Test
    fun `median index skips unmeasured values and is null when none are measured`() {
        assertEquals(3, medianIndex(listOf(null, 1.0, 3.0, 2.0)))
        assertNull(medianIndex(listOf(null, null)))
    }

    private fun scroll(scenario: String, p50: Double, frames: Int = 100) = UiResult(
        scenario = scenario,
        frames = frames,
        frameP50Ms = p50,
        frameP90Ms = p50 * 2,
        frameP99Ms = p50 * 3,
    )

    private fun ui(vararg results: UiResult): Pair<String, Axis> =
        "ui" to UiAxis(config = UiAxisConfig(iterations = 5), results = results.toList())

    private fun audio(vararg results: AudioResult): Pair<String, Axis> =
        "audio" to AudioAxis(config = AudioAxisConfig(sampleSeconds = 30, repeats = 3), results = results.toList())

    private fun run(generated: String, axis: Pair<String, Axis>) = PerfResult(
        generated = generated,
        config = PerfConfig(scenarios = emptyList()),
        machine = DeviceFingerprint(
            manufacturer = "Google",
            model = "Pixel 7",
            androidBuild = "UQ1A",
            abi = "arm64-v8a",
            thermalStatus = "none",
            chipboxCommit = "abc1234",
            sageCommit = "def5678",
        ),
        corpus = CorpusRef(root = "/corpus"),
        axes = mapOf(axis),
    )
}
