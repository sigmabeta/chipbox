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
    fun `compares matching runs per scenario with a speedup above one for a faster current`() {
        val baseline = result(results = listOf(audio("audio.usf", rtf = 10.0, medianSeconds = 3.0)))
        val current = result(results = listOf(audio("audio.usf", rtf = 20.0, medianSeconds = 1.5)))

        val compared = assertIs<DiffOutcome.Compared>(compareAudioRuns(baseline, current))

        val speedup = compared.report.scenarios.single()
        assertEquals("audio.usf", speedup.scenario)
        assertEquals(10.0, speedup.baselineRtf, TOLERANCE)
        assertEquals(20.0, speedup.currentRtf, TOLERANCE)
        assertEquals(2.0, speedup.speedup, TOLERANCE)
        assertEquals(2.0, compared.report.geometricMean, TOLERANCE)
    }

    @Test
    fun `refuses a host run against a device run`() {
        val baseline = result(machine = hostFingerprint())
        val current = result(machine = deviceFingerprint())

        val refused = assertIs<DiffOutcome.Refused>(compareAudioRuns(baseline, current))

        assertIs<DiffRefusal.FingerprintClassMismatch>(refused.reason)
    }

    @Test
    fun `refuses runs that measured a different scenario set`() {
        val baseline = result(results = listOf(audio("audio.usf", 10.0), audio("audio.vgm", 10.0)))
        val current = result(results = listOf(audio("audio.usf", 10.0)))

        val refused = assertIs<DiffOutcome.Refused>(compareAudioRuns(baseline, current))

        val mismatch = assertIs<DiffRefusal.ScenarioSetMismatch>(refused.reason)
        assertEquals(listOf("audio.vgm"), mismatch.baselineOnly)
        assertEquals(emptyList(), mismatch.currentOnly)
    }

    @Test
    fun `refuses runs measured over a different corpus manifest`() {
        val baseline = result(manifest = manifest("game/a.usf"))
        val current = result(manifest = manifest("game/b.usf"))

        val refused = assertIs<DiffOutcome.Refused>(compareAudioRuns(baseline, current))

        assertIs<DiffRefusal.CorpusMismatch>(refused.reason)
    }

    @Test
    fun `refuses runs whose manifest holds the same files in a different order`() {
        val baseline = result(manifest = manifest("game/a.usf", "game/b.usf"))
        val current = result(manifest = manifest("game/b.usf", "game/a.usf"))

        val refused = assertIs<DiffOutcome.Refused>(compareAudioRuns(baseline, current))

        assertIs<DiffRefusal.CorpusMismatch>(refused.reason)
    }

    @Test
    fun `refuses when both runs measured no scenarios`() {
        val baseline = result(results = emptyList())
        val current = result(results = emptyList())

        val refused = assertIs<DiffOutcome.Refused>(compareAudioRuns(baseline, current))

        assertIs<DiffRefusal.EmptyScenarioSet>(refused.reason)
    }

    @Test
    fun `a matching run compares even when the machines within the class differ`() {
        val baseline = result(machine = hostFingerprint(cpuModel = "one", cpuCores = 8))
        val current = result(machine = hostFingerprint(cpuModel = "two", cpuCores = 16))

        assertIs<DiffOutcome.Compared>(compareAudioRuns(baseline, current))
    }

    private fun result(
        machine: Fingerprint = hostFingerprint(),
        manifest: List<CorpusEntry> = manifest("game/track.usf"),
        results: List<AudioResult> = listOf(audio("audio.usf", rtf = 10.0, medianSeconds = 3.0)),
    ): PerfResult = PerfResult(
        generated = "2026-10-13T12:00:00+00:00",
        config = PerfConfig(scenarios = results.map { it.scenario }),
        machine = machine,
        corpus = CorpusRef(root = "/corpus", manifest = manifest),
        axes = mapOf("audio" to AudioAxis(AudioAxisConfig(sampleSeconds = 30, repeats = 3), results)),
    )

    private fun audio(scenario: String, rtf: Double, medianSeconds: Double = 3.0): AudioResult = AudioResult(
        scenario = scenario,
        rtf = rtf,
        cpuSeconds = null,
        median = medianSeconds,
        min = medianSeconds,
        max = medianSeconds,
        samples = listOf(medianSeconds),
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
