package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DiffTest {
    @Test
    fun `geometric mean summarises ratios and cancels symmetric changes`() {
        assertEquals(2.0, geometricMean(listOf(1.0, 4.0)), TOLERANCE)
        assertEquals(4.0, geometricMean(listOf(2.0, 8.0)), TOLERANCE)
        assertEquals(2.0, geometricMean(listOf(2.0, 2.0, 2.0)), TOLERANCE)
        assertEquals(1.0, geometricMean(listOf(2.0, 0.5)), TOLERANCE)
        assertEquals(0.5, geometricMean(listOf(0.5)), TOLERANCE)
    }

    @Test
    fun `geometric mean rejects empty and non-positive input`() {
        assertFailsWith<IllegalArgumentException> { geometricMean(emptyList()) }
        assertFailsWith<IllegalArgumentException> { geometricMean(listOf(1.0, 0.0)) }
        assertFailsWith<IllegalArgumentException> { geometricMean(listOf(1.0, -2.0)) }
    }

    @Test
    fun `compares the audio axis by render median keyed by path with a ratio above one for a faster current`() {
        val baseline = result(axes = mapOf("audio" to audioAxis(audio("Chrono Trigger/track.spc", medianSeconds = 3.0))))
        val current = result(axes = mapOf("audio" to audioAxis(audio("Chrono Trigger/track.spc", medianSeconds = 1.5))))

        val compared = assertIs<DiffOutcome.Compared>(compareRuns(baseline, current))

        val axis = compared.report.axes.single()
        assertEquals("audio", axis.axis)
        val scenario = axis.scenarios.single()
        assertEquals("Chrono Trigger/track.spc", scenario.scenario)
        assertEquals(3.0, scenario.baselineMetric, TOLERANCE)
        assertEquals(1.5, scenario.currentMetric, TOLERANCE)
        assertEquals(2.0, scenario.ratio, TOLERANCE)
        assertEquals(2.0, axis.geometricMean, TOLERANCE)
        assertEquals(2.0, compared.report.geometricMean, TOLERANCE)
    }

    @Test
    fun `pairs audio results by path and skips an errored entry`() {
        val baseline = result(
            axes = mapOf(
                "audio" to audioAxis(
                    audio("Chrono Trigger/track.spc", medianSeconds = 2.0),
                    erroredAudio("Chrono Trigger/broken.spc"),
                ),
            ),
        )
        val current = result(
            axes = mapOf(
                "audio" to audioAxis(
                    audio("Chrono Trigger/track.spc", medianSeconds = 1.0),
                    erroredAudio("Chrono Trigger/broken.spc"),
                ),
            ),
        )

        val compared = assertIs<DiffOutcome.Compared>(compareRuns(baseline, current))

        val scenarios = compared.report.axes.single().scenarios
        val scenario = scenarios.single()
        assertEquals("Chrono Trigger/track.spc", scenario.scenario)
        assertEquals(2.0, scenario.baselineMetric, TOLERANCE)
        assertEquals(1.0, scenario.currentMetric, TOLERANCE)
        assertEquals(2.0, scenario.ratio, TOLERANCE)
    }

    @Test
    fun `compares the ui axis by frame p50`() {
        val baseline = result(axes = mapOf("ui" to uiAxis(ui("ui.browse_by_game_scroll", frameP50Ms = 12.0))))
        val current = result(axes = mapOf("ui" to uiAxis(ui("ui.browse_by_game_scroll", frameP50Ms = 8.0))))

        val compared = assertIs<DiffOutcome.Compared>(compareRuns(baseline, current))

        val axis = compared.report.axes.single()
        assertEquals("ui", axis.axis)
        val scenario = axis.scenarios.single()
        assertEquals(12.0, scenario.baselineMetric, TOLERANCE)
        assertEquals(8.0, scenario.currentMetric, TOLERANCE)
        assertEquals(1.5, scenario.ratio, TOLERANCE)
    }

    @Test
    fun `compares the startup axis by time to first content p50`() {
        val baseline = result(axes = mapOf("startup" to startupAxis(startup("startup.cold", p50Ms = 400.0))))
        val current = result(axes = mapOf("startup" to startupAxis(startup("startup.cold", p50Ms = 500.0))))

        val compared = assertIs<DiffOutcome.Compared>(compareRuns(baseline, current))

        val axis = compared.report.axes.single()
        assertEquals("startup", axis.axis)
        val scenario = axis.scenarios.single()
        assertEquals(400.0, scenario.baselineMetric, TOLERANCE)
        assertEquals(500.0, scenario.currentMetric, TOLERANCE)
        assertEquals(0.8, scenario.ratio, TOLERANCE)
    }

    @Test
    fun `compares every axis present in both runs with per-axis and overall geometric means`() {
        val baseline = result(
            axes = mapOf(
                "audio" to audioAxis(audio("game/track.spc", medianSeconds = 2.0)),
                "ui" to uiAxis(ui("ui.browse_by_game_scroll", frameP50Ms = 4.0)),
                "startup" to startupAxis(startup("startup.cold", p50Ms = 8.0)),
            ),
        )
        val current = result(
            axes = mapOf(
                "audio" to audioAxis(audio("game/track.spc", medianSeconds = 2.0)),
                "ui" to uiAxis(ui("ui.browse_by_game_scroll", frameP50Ms = 2.0)),
                "startup" to startupAxis(startup("startup.cold", p50Ms = 2.0)),
            ),
        )

        val compared = assertIs<DiffOutcome.Compared>(compareRuns(baseline, current))

        assertEquals(listOf("audio", "startup", "ui"), compared.report.axes.map { it.axis })
        assertEquals(1.0, compared.report.axes.first { it.axis == "audio" }.geometricMean, TOLERANCE)
        assertEquals(4.0, compared.report.axes.first { it.axis == "startup" }.geometricMean, TOLERANCE)
        assertEquals(2.0, compared.report.axes.first { it.axis == "ui" }.geometricMean, TOLERANCE)
        assertEquals(2.0, compared.report.geometricMean, TOLERANCE)
    }

    @Test
    fun `carries each axis's primary metric label`() {
        val baseline = result(
            axes = mapOf(
                "audio" to audioAxis(audio("game/track.spc", medianSeconds = 2.0)),
                "ui" to uiAxis(ui("ui.browse_by_game_scroll", frameP50Ms = 4.0)),
                "startup" to startupAxis(startup("startup.cold", p50Ms = 8.0)),
            ),
        )

        val compared = assertIs<DiffOutcome.Compared>(compareRuns(baseline, baseline))

        val labels = compared.report.axes.associate { it.axis to it.metricLabel }
        assertEquals("render wall median (s)", labels.getValue("audio"))
        assertEquals("frame p50 (ms)", labels.getValue("ui"))
        assertEquals("time to first content p50 (ms)", labels.getValue("startup"))
    }

    @Test
    fun `compares a shared non-audio axis instead of refusing with nothing to compare`() {
        val baseline = result(
            machine = deviceFingerprint(),
            axes = mapOf(
                "ui" to uiAxis(ui("ui.browse_by_game_scroll", frameP50Ms = 12.0)),
                "startup" to startupAxis(startup("startup.cold", p50Ms = 400.0)),
            ),
        )
        val current = result(
            machine = deviceFingerprint(),
            axes = mapOf(
                "ui" to uiAxis(ui("ui.browse_by_game_scroll", frameP50Ms = 9.0)),
                "startup" to startupAxis(startup("startup.cold", p50Ms = 400.0)),
            ),
        )

        val compared = assertIs<DiffOutcome.Compared>(compareRuns(baseline, current))

        assertEquals(listOf("startup", "ui"), compared.report.axes.map { it.axis })
        assertEquals(1.0, compared.report.axes.first { it.axis == "startup" }.geometricMean, TOLERANCE)
        assertEquals(4.0 / 3.0, compared.report.axes.first { it.axis == "ui" }.geometricMean, TOLERANCE)
    }

    @Test
    fun `refuses a host run against a device run`() {
        val baseline = result(machine = hostFingerprint())
        val current = result(machine = deviceFingerprint())

        val refused = assertIs<DiffOutcome.Refused>(compareRuns(baseline, current))

        assertIs<DiffRefusal.FingerprintClassMismatch>(refused.reason)
    }

    @Test
    fun `refuses an audio path-set difference`() {
        val baseline = result(
            axes = mapOf("audio" to audioAxis(audio("game/a.spc"))),
        )
        val current = result(
            axes = mapOf("audio" to audioAxis(audio("game/b.spc"))),
        )

        val refused = assertIs<DiffOutcome.Refused>(compareRuns(baseline, current))

        val mismatch = assertIs<DiffRefusal.ScenarioSetMismatch>(refused.reason)
        assertEquals(listOf("audio:game/a.spc"), mismatch.baselineOnly)
        assertEquals(listOf("audio:game/b.spc"), mismatch.currentOnly)
    }

    @Test
    fun `refuses runs whose scenario sets differ across axes`() {
        val baseline = result(
            machine = deviceFingerprint(),
            axes = mapOf("ui" to uiAxis(ui("ui.browse_by_game_scroll", frameP50Ms = 12.0))),
        )
        val current = result(
            machine = deviceFingerprint(),
            axes = mapOf("startup" to startupAxis(startup("startup.cold", p50Ms = 400.0))),
        )

        val refused = assertIs<DiffOutcome.Refused>(compareRuns(baseline, current))

        val mismatch = assertIs<DiffRefusal.ScenarioSetMismatch>(refused.reason)
        assertEquals(listOf("ui:ui.browse_by_game_scroll"), mismatch.baselineOnly)
        assertEquals(listOf("startup:startup.cold"), mismatch.currentOnly)
    }

    @Test
    fun `refuses runs measured over a different corpus manifest`() {
        val baseline = result(manifest = manifest("game/a.usf"))
        val current = result(manifest = manifest("game/b.usf"))

        val refused = assertIs<DiffOutcome.Refused>(compareRuns(baseline, current))

        assertIs<DiffRefusal.CorpusMismatch>(refused.reason)
    }

    @Test
    fun `refuses runs whose manifest holds the same files in a different order`() {
        val baseline = result(manifest = manifest("game/a.usf", "game/b.usf"))
        val current = result(manifest = manifest("game/b.usf", "game/a.usf"))

        val refused = assertIs<DiffOutcome.Refused>(compareRuns(baseline, current))

        assertIs<DiffRefusal.CorpusMismatch>(refused.reason)
    }

    @Test
    fun `refuses when both runs measured no scenarios`() {
        val baseline = result(axes = mapOf("audio" to audioAxis()))
        val current = result(axes = mapOf("audio" to audioAxis()))

        val refused = assertIs<DiffOutcome.Refused>(compareRuns(baseline, current))

        assertIs<DiffRefusal.EmptyScenarioSet>(refused.reason)
    }

    @Test
    fun `refuses when every audio file errored so there is nothing measurable`() {
        val baseline = result(axes = mapOf("audio" to audioAxis(erroredAudio("game/a.spc"))))
        val current = result(axes = mapOf("audio" to audioAxis(erroredAudio("game/a.spc"))))

        val refused = assertIs<DiffOutcome.Refused>(compareRuns(baseline, current))

        assertIs<DiffRefusal.EmptyScenarioSet>(refused.reason)
    }

    @Test
    fun `a matching run compares even when the machines within the class differ`() {
        val baseline = result(machine = hostFingerprint(cpuModel = "one", cpuCores = 8))
        val current = result(machine = hostFingerprint(cpuModel = "two", cpuCores = 16))

        assertIs<DiffOutcome.Compared>(compareRuns(baseline, current))
    }

    private fun result(
        machine: Fingerprint = hostFingerprint(),
        manifest: List<CorpusEntry> = manifest("game/track.usf"),
        axes: Map<String, Axis> = mapOf("audio" to audioAxis(audio("game/track.spc", medianSeconds = 3.0))),
    ): PerfResult = PerfResult(
        generated = "2026-10-13T12:00:00+00:00",
        config = PerfConfig(scenarios = emptyList()),
        machine = machine,
        corpus = CorpusRef(root = "/corpus", manifest = manifest),
        axes = axes,
    )

    private fun audioAxis(vararg results: AudioResult): AudioAxis =
        AudioAxis(AudioAxisConfig(sampleSeconds = 30, repeats = 3), results.toList())

    private fun uiAxis(vararg results: UiResult): UiAxis =
        UiAxis(UiAxisConfig(iterations = 5), results.toList())

    private fun startupAxis(vararg results: StartupResult): StartupAxis =
        StartupAxis(StartupAxisConfig(iterations = 5), results.toList())

    private fun audio(path: String, medianSeconds: Double = 3.0): AudioResult = AudioResult(
        path = path,
        rtf = 10.0,
        cpuSeconds = null,
        median = medianSeconds,
        min = medianSeconds,
        max = medianSeconds,
        samples = listOf(medianSeconds),
    )

    private fun erroredAudio(path: String): AudioResult = AudioResult(path = path, error = "render failed")

    private fun ui(scenario: String, frameP50Ms: Double): UiResult = UiResult(
        scenario = scenario,
        frames = 100,
        frameP50Ms = frameP50Ms,
        frameP90Ms = frameP50Ms,
        frameP99Ms = frameP50Ms,
        jankPercent = null,
        traceSections = emptyList(),
    )

    private fun startup(scenario: String, p50Ms: Double): StartupResult = StartupResult(
        scenario = scenario,
        timeToFirstContentP50Ms = p50Ms,
        timeToFirstContentP90Ms = p50Ms,
        timeToFirstContentP99Ms = p50Ms,
        samplesMs = listOf(p50Ms),
    )

    private fun hostFingerprint(cpuModel: String = "CPU", cpuCores: Int = 8): HostFingerprint =
        HostFingerprint(cpuModel, cpuCores, "Linux", "6.8", "JVM", "abc1234", "def5678")

    private fun deviceFingerprint(): DeviceFingerprint =
        DeviceFingerprint("Google", "Pixel 8", "AP1A", "arm64-v8a", "none", "abc1234", "def5678")

    private fun manifest(vararg paths: String): List<CorpusEntry> =
        paths.mapIndexed { index, path -> CorpusEntry(path, sha256 = "hash$index", bytes = 100L + index) }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
