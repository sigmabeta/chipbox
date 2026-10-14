package net.sigmabeta.chipbox.perfbench

/**
 * The raw timings for one UI scenario: the pooled frame CPU durations and frame overruns across
 * iterations, plus the labelled trace-section sums. Turning this into a [UiResult] is pure, so the
 * report math is unit-tested without a device. [frameOverrunMillis] may be empty on devices that
 * can't report frame overrun, in which case jank is not computed.
 */
data class UiMeasurement(
    val scenario: String,
    val frameCpuMillis: List<Double>,
    val frameOverrunMillis: List<Double> = emptyList(),
    val traceSections: List<TraceSectionMeasurement> = emptyList(),
)

/** One labelled trace section's pooled measurement: total time in the section and its instance count. */
data class TraceSectionMeasurement(
    val name: String,
    val sumMs: Double,
    val count: Int,
)

/**
 * Assembles one scenario's [UiResult].
 *
 * Frame-time percentiles come from a linear-interpolation percentile over the pooled frame CPU
 * durations — the same estimator the macrobenchmark library uses — so a single slow frame moves
 * p99 without dragging the median. Jank is the share of frames whose overrun was positive (they
 * missed their deadline); it is `null` when no overrun samples were captured.
 */
fun uiResult(measurement: UiMeasurement): UiResult {
    require(measurement.frameCpuMillis.isNotEmpty()) { "at least one frame duration is required" }

    val sorted = measurement.frameCpuMillis.sorted()
    return UiResult(
        scenario = measurement.scenario,
        frames = sorted.size,
        frameP50Ms = percentile(sorted, 50),
        frameP90Ms = percentile(sorted, 90),
        frameP99Ms = percentile(sorted, 99),
        jankPercent = measurement.frameOverrunMillis.takeIf { it.isNotEmpty() }?.let(::jankPercent),
        traceSections = measurement.traceSections.map {
            TraceSectionSum(name = it.name, sumMs = it.sumMs, count = it.count)
        },
    )
}

/**
 * The [percentile]th percentile of [sorted] by linear interpolation between the two bracketing
 * samples — matching the macrobenchmark library's estimator, so our numbers are comparable to its
 * own P50/P90/P99 rather than a different estimator's. [sorted] must be non-empty and ascending.
 */
fun percentile(sorted: List<Double>, percentile: Int): Double {
    require(sorted.isNotEmpty()) { "percentile of an empty list is undefined" }
    val idealIndex = percentile.coerceIn(0, 100) / 100.0 * (sorted.size - 1)
    val lower = idealIndex.toInt()
    val upper = sorted.getOrElse(lower + 1) { sorted[lower] }
    val ratio = idealIndex - lower
    return sorted[lower] * (1 - ratio) + upper * ratio
}

/** The share of frames (as a percentage) whose overrun was positive, i.e. missed their deadline. */
fun jankPercent(frameOverrunMillis: List<Double>): Double {
    require(frameOverrunMillis.isNotEmpty()) { "jank percent of an empty list is undefined" }
    return frameOverrunMillis.count { it > 0.0 } * 100.0 / frameOverrunMillis.size
}
