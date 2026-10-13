package net.sigmabeta.chipbox.perfbench

import kotlin.math.exp
import kotlin.math.ln

/**
 * One scenario compared between a baseline run and a current run.
 *
 * [speedup] is the ratio of the baseline's render time to the current's, so a value above one means
 * the current run rendered the same audio faster. Because RTF is audio duration ÷ render time over
 * the same sample wall, this is also current RTF ÷ baseline RTF. The two RTFs are carried alongside
 * so a report can show the headline metric the speedup was derived from.
 */
data class ScenarioSpeedup(
    val scenario: String,
    val baselineRtf: Double,
    val currentRtf: Double,
    val speedup: Double,
)

/** Every compared scenario plus their summary: the geometric mean of the per-scenario speedups. */
data class DiffReport(
    val scenarios: List<ScenarioSpeedup>,
    val geometricMean: Double,
)

/**
 * Why two runs may not be compared. Each variant names what differed so a report can explain the
 * refusal instead of printing a silently wrong speedup.
 */
sealed interface DiffRefusal {
    val message: String

    /** A host run and a device run describe different machine classes. */
    data class FingerprintClassMismatch(val baseline: FingerprintClass, val current: FingerprintClass) : DiffRefusal {
        override val message: String =
            "fingerprint class differs (${baseline.label} vs ${current.label}) — " +
                "a ${baseline.label} run and a ${current.label} run are not comparable"
    }

    /** The two runs measured a different set of scenarios, so not every scenario has a baseline. */
    data class ScenarioSetMismatch(val baselineOnly: List<String>, val currentOnly: List<String>) : DiffRefusal {
        override val message: String = buildString {
            append("scenario set differs")
            if (baselineOnly.isNotEmpty()) append(" — only baseline: ${baselineOnly.joinToString(", ")}")
            if (currentOnly.isNotEmpty()) append(" — only current: ${currentOnly.joinToString(", ")}")
        }
    }

    /** The runs were measured over different corpora; [detail] names where they diverge. */
    data class CorpusMismatch(val detail: String) : DiffRefusal {
        override val message: String =
            "corpus manifest differs ($detail) — the runs were not measured over identical input"
    }

    /** Both runs carry no measurable scenarios, so there is nothing to compare. */
    data object EmptyScenarioSet : DiffRefusal {
        override val message: String = "both runs measured no scenarios — nothing to compare"
    }
}

/** The result of comparing two runs: either a refusal, or the per-scenario report. */
sealed interface DiffOutcome {
    data class Refused(val reason: DiffRefusal) : DiffOutcome
    data class Compared(val report: DiffReport) : DiffOutcome
}

/** The machine class a run belongs to — results are only compared within one class. */
enum class FingerprintClass(val label: String) {
    HOST("host"),
    DEVICE("device"),
}

/** The class of a run's fingerprint. */
val Fingerprint.fingerprintClass: FingerprintClass
    get() = when (this) {
        is HostFingerprint -> FingerprintClass.HOST
        is DeviceFingerprint -> FingerprintClass.DEVICE
    }

/**
 * Compares the audio axis of two runs, refusing unless they are comparable.
 *
 * The guards run before any ratio is computed: a different fingerprint class, a different scenario
 * set, or a different corpus manifest means no comparison is meaningful, so the caller is handed a
 * [DiffRefusal] rather than a number. When all three match, each scenario is paired by its id and
 * its speedup reported, plus the geometric mean of those speedups.
 */
fun compareAudioRuns(baseline: PerfResult, current: PerfResult): DiffOutcome {
    val refusal = comparabilityRefusal(baseline, current)
    if (refusal != null) return DiffOutcome.Refused(refusal)

    val baselineByScenario = baseline.audioResultsById()
    val currentByScenario = current.audioResultsById()
    val speedups = baselineByScenario.keys.sorted().map { scenario ->
        val baselineResult = baselineByScenario.getValue(scenario)
        val currentResult = currentByScenario.getValue(scenario)
        ScenarioSpeedup(
            scenario = scenario,
            baselineRtf = baselineResult.rtf,
            currentRtf = currentResult.rtf,
            speedup = baselineResult.median / currentResult.median,
        )
    }
    return DiffOutcome.Compared(DiffReport(speedups, geometricMean(speedups.map { it.speedup })))
}

/** The first reason the two runs are not comparable, or `null` when every guard passes. */
private fun comparabilityRefusal(baseline: PerfResult, current: PerfResult): DiffRefusal? {
    val baselineClass = baseline.machine.fingerprintClass
    val currentClass = current.machine.fingerprintClass
    val baselineScenarios = baseline.audioResultsById().keys
    val currentScenarios = current.audioResultsById().keys
    return when {
        baselineClass != currentClass -> DiffRefusal.FingerprintClassMismatch(baselineClass, currentClass)

        baselineScenarios != currentScenarios -> DiffRefusal.ScenarioSetMismatch(
            baselineOnly = (baselineScenarios - currentScenarios).sorted(),
            currentOnly = (currentScenarios - baselineScenarios).sorted(),
        )

        baselineScenarios.isEmpty() -> DiffRefusal.EmptyScenarioSet

        baseline.corpus.manifest != current.corpus.manifest ->
            DiffRefusal.CorpusMismatch(manifestDifference(baseline.corpus.manifest, current.corpus.manifest))

        else -> null
    }
}

/** A short description of where two ordered manifests first diverge, for a refusal message. */
private fun manifestDifference(baseline: List<CorpusEntry>, current: List<CorpusEntry>): String {
    if (baseline.size != current.size) return "${baseline.size} vs ${current.size} files"
    val index = baseline.indices.firstOrNull { baseline[it] != current[it] }
    return when {
        index == null -> "entry ordering differs"
        baseline[index].path == current[index].path -> "content differs at ${baseline[index].path}"
        else -> "path differs at entry $index: ${baseline[index].path} vs ${current[index].path}"
    }
}

/** The run's audio results keyed by scenario id. */
private fun PerfResult.audioResultsById(): Map<String, AudioResult> =
    (axes["audio"] as? AudioAxis)?.results.orEmpty().associateBy { it.scenario }

/**
 * The geometric mean of [values] — the right summary for ratios, because a 2× speedup and a 2×
 * slowdown cancel. Computed through logs so a long list of large ratios cannot overflow.
 */
fun geometricMean(values: List<Double>): Double {
    require(values.isNotEmpty()) { "geometric mean of an empty list is undefined" }
    require(values.all { it > 0.0 }) { "geometric mean requires positive values, was $values" }
    return exp(values.sumOf { ln(it) } / values.size)
}
