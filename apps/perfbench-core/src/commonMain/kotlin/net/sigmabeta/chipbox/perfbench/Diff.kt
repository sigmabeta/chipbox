package net.sigmabeta.chipbox.perfbench

import kotlin.math.exp
import kotlin.math.ln

/**
 * One scenario compared between a baseline run and a current run, within its enclosing
 * [AxisComparison].
 *
 * [baselineMetric]/[currentMetric] are that axis's own primary cost metric, and [ratio] is the
 * baseline value divided by the current one. Every primary metric is lower-is-better, so a ratio
 * above one means the current run is better. The two raw values are carried alongside so a report
 * can show the metric the ratio was derived from.
 */
data class ScenarioComparison(
    val scenario: String,
    val baselineMetric: Double,
    val currentMetric: Double,
    val ratio: Double,
)

/**
 * Every scenario compared in one axis, plus that axis's summary: the geometric mean of its
 * per-scenario ratios. [metricLabel] names the primary metric the ratios were derived from, so a
 * report can say what its numbers mean without knowing the axis's schema.
 */
data class AxisComparison(
    val axis: String,
    val metricLabel: String,
    val scenarios: List<ScenarioComparison>,
    val geometricMean: Double,
)

/** Every compared axis, plus the overall geometric mean across every compared scenario. */
data class DiffReport(
    val axes: List<AxisComparison>,
    val geometricMean: Double,
)

/**
 * Why two runs may not be compared. Each variant names what differed so a report can explain the
 * refusal instead of printing a silently wrong ratio.
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

/** The result of comparing two runs: either a refusal, or the per-axis report. */
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
 * Compares every axis present in both runs, refusing unless they are comparable.
 *
 * The guards run before any ratio is computed: a different fingerprint class, a different scenario
 * set, or a different corpus manifest means no comparison is meaningful, so the caller is handed a
 * [DiffRefusal] rather than a number. When all three match, each axis's scenarios are paired by its
 * own key (a corpus-relative path for audio, a scenario id otherwise) and their ratio computed from
 * that axis's [primaryMetric], plus the geometric mean of each axis's ratios and of every scenario
 * overall.
 */
fun compareRuns(baseline: PerfResult, current: PerfResult): DiffOutcome {
    val refusal = comparabilityRefusal(baseline, current)
    if (refusal != null) return DiffOutcome.Refused(refusal)

    val axes = baseline.axes.keys.intersect(current.axes.keys).sorted().mapNotNull { axisId ->
        val baselineMetric = baseline.axes.getValue(axisId).primaryMetric()
        val currentValues = current.axes.getValue(axisId).primaryMetric().values
        // Intersect the measurable keys: an entry errored on one side is recorded but not compared.
        val keys = baselineMetric.values.keys.intersect(currentValues.keys).sorted()
        if (keys.isEmpty()) return@mapNotNull null

        val scenarios = keys.map { key ->
            val baselineValue = baselineMetric.values.getValue(key)
            val currentValue = currentValues.getValue(key)
            ScenarioComparison(
                scenario = key,
                baselineMetric = baselineValue,
                currentMetric = currentValue,
                ratio = baselineValue / currentValue,
            )
        }
        AxisComparison(
            axis = axisId,
            metricLabel = baselineMetric.label,
            scenarios = scenarios,
            geometricMean = geometricMean(scenarios.map { it.ratio }),
        )
    }
    val ratios = axes.flatMap { it.scenarios }.map { it.ratio }
    if (ratios.isEmpty()) return DiffOutcome.Refused(DiffRefusal.EmptyScenarioSet)
    return DiffOutcome.Compared(DiffReport(axes, geometricMean(ratios)))
}

/** The first reason the two runs are not comparable, or `null` when every guard passes. */
private fun comparabilityRefusal(baseline: PerfResult, current: PerfResult): DiffRefusal? {
    val baselineClass = baseline.machine.fingerprintClass
    val currentClass = current.machine.fingerprintClass
    val baselineScenarios = baseline.scenarioKeys()
    val currentScenarios = current.scenarioKeys()
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

/** Every scenario the run measured, across every axis, as `axis:scenario` keys. */
private fun PerfResult.scenarioKeys(): Set<String> =
    axes.entries.flatMap { (axis, value) ->
        value.resultKeys().map { key -> "$axis:$key" }
    }.toSet()

/** Every result identity an axis carries, errored included, so the file-set guard stays honest. */
private fun Axis.resultKeys(): List<String> = when (this) {
    is AudioAxis -> results.map { it.path }
    is UiAxis -> results.map { it.scenario }
    is StartupAxis -> results.map { it.scenario }
}

/** One axis's primary cost metric: what its ratios are computed from, and how to label it. */
private data class PrimaryMetric(val label: String, val values: Map<String, Double>)

/**
 * The single per-axis metric lookup: the headline cost of each axis's scenarios, lower-is-better.
 *
 * The audio axis is measured by its render wall median (seconds), not its `rtf`: the diff contract
 * is `baseline ÷ current` over lower-is-better costs, and RTF is the inverse of that wall time, so
 * deriving from RTF would flip the direction. The "wall" in the label keeps an audio row from being
 * read as an RTF speedup.
 *
 * [Axis] is sealed, so a new axis cannot be added without this `when` failing to compile — there is
 * one place, not a cascade, that decides what each axis is measured by.
 */
private fun Axis.primaryMetric(): PrimaryMetric = when (this) {
    is AudioAxis -> PrimaryMetric(
        label = "render wall median (s)",
        values = results.filter { it.error == null }.associate { it.path to it.median!! },
    )

    is UiAxis -> PrimaryMetric(
        label = "frame p50 (ms)",
        values = results.associate { it.scenario to it.frameP50Ms },
    )

    is StartupAxis -> PrimaryMetric(
        label = "time to first content p50 (ms)",
        values = results.associate { it.scenario to it.timeToFirstContentP50Ms },
    )
}

/**
 * The geometric mean of [values] — the right summary for ratios, because a 2× improvement and a 2×
 * regression cancel. Computed through logs so a long list of large ratios cannot overflow.
 */
fun geometricMean(values: List<Double>): Double {
    require(values.isNotEmpty()) { "geometric mean of an empty list is undefined" }
    require(values.all { it > 0.0 }) { "geometric mean requires positive values, was $values" }
    return exp(values.sumOf { ln(it) } / values.size)
}
