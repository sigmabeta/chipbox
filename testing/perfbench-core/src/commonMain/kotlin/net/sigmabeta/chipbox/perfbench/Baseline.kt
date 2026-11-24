package net.sigmabeta.chipbox.perfbench

/**
 * The result of merging recordings into a baseline: a refusal, or the merged document plus each
 * scenario's [ScenarioSpread] across the recordings.
 */
sealed interface BaselineOutcome {
    data class Refused(val reason: DiffRefusal) : BaselineOutcome
    data class Merged(val result: PerfResult, val spreads: List<ScenarioSpread>) : BaselineOutcome
}

/**
 * One scenario's primary metric across the recordings, in recording order, labelled with the axis
 * metric it is. [chosen] is the index of the recording the baseline took.
 */
data class ScenarioSpread(
    val axis: String,
    val metricLabel: String,
    val scenario: String,
    val values: List<Double?>,
    val chosen: Int?,
)

/**
 * Merges several recordings of the same device, corpus and scenario set into one baseline: for each
 * scenario, the recording whose primary metric (the value [compareRuns] gates on) is the median.
 *
 * One recording is a single draw from a noisy distribution. A baseline that happens to be a low or
 * a high draw biases every later comparison against it, so a fast baseline makes the gate report
 * regressions that aren't there. The median of several recordings is a typical draw.
 *
 * Each scenario keeps one recording's whole entry, not a per-field median. A frame p50 from one
 * run next to a p90 and frame count from another would describe a run that never happened. With
 * an even count the lower median (the faster recording) is taken. An entry no recording could
 * measure (an errored audio file) is kept from the newest recording.
 *
 * The document's run-wide blocks (fingerprint, corpus, config, `generated`) come from the newest
 * recording, the last in [recordings]. Recordings that [compareRuns] would refuse to compare with
 * each other are refused here too, so a baseline never mixes corpora or scenario sets.
 */
fun medianBaseline(recordings: List<PerfResult>): BaselineOutcome {
    require(recordings.isNotEmpty()) { "a baseline needs at least one recording" }
    val newest = recordings.last()
    recordings.forEach { recording ->
        comparabilityRefusal(newest, recording)?.let { return BaselineOutcome.Refused(it) }
    }
    val axes = newest.axes.mapValues { (axisId, axis) ->
        medianAxis(axis, recordings.mapNotNull { it.axes[axisId] })
    }
    val spreads = newest.axes.toSortedMap().flatMap { (axisId, axis) ->
        val metric = axis.primaryMetric()
        val costs = recordings.map { it.axes[axisId]?.primaryMetric()?.values.orEmpty() }
        metric.values.keys.sorted().map { key ->
            val values = costs.map { it[key] }
            ScenarioSpread(axisId, metric.label, key, values, medianIndex(values))
        }
    }
    return BaselineOutcome.Merged(newest.copy(axes = axes), spreads)
}

/** [newest] with each of its entries replaced by the median recording's entry for the same key. */
private fun medianAxis(newest: Axis, recordings: List<Axis>): Axis {
    val costs = recordings.map { it.primaryMetric().values }

    fun <R> median(entries: List<R>, key: (R) -> String, results: (Axis) -> List<R>): List<R> =
        entries.map { entry ->
            val id = key(entry)
            val index = medianIndex(costs.map { it[id] }) ?: return@map entry
            results(recordings[index]).firstOrNull { key(it) == id } ?: entry
        }

    return when (newest) {
        is AudioAxis -> newest.copy(
            results = median(newest.results, AudioResult::path) { (it as? AudioAxis)?.results.orEmpty() },
        )

        is UiAxis -> newest.copy(
            results = median(newest.results, UiResult::scenario) { (it as? UiAxis)?.results.orEmpty() },
        )

        is StartupAxis -> newest.copy(
            results = median(newest.results, StartupResult::scenario) { (it as? StartupAxis)?.results.orEmpty() },
        )
    }
}

/**
 * The index of the (lower) median among the non-null [values], or `null` when none is measurable.
 * Ties keep recording order, so equal values resolve to the earlier recording.
 */
internal fun medianIndex(values: List<Double?>): Int? {
    val measured = values.withIndex().filter { it.value != null }.sortedBy { it.value }
    if (measured.isEmpty()) return null
    return measured[(measured.size - 1) / 2].index
}
