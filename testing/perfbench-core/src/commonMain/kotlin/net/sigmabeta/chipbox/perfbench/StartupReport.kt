package net.sigmabeta.chipbox.perfbench

/**
 * The raw timings for one startup scenario: time-to-first-content, in milliseconds, across the
 * measured launches. Turning this into a [StartupResult] is pure, so the report math is unit-tested
 * without a device.
 *
 * "First content" is the library grid showing its first items — the point at which the app reports
 * itself fully drawn (see the Android `benchmark` build type's first-content reporter), which the
 * macrobenchmark's `StartupTimingMetric` records as `timeToFullDisplayMs`.
 */
data class StartupMeasurement(
    val scenario: String,
    val timeToFirstContentMillis: List<Double>,
)

/**
 * Assembles one startup scenario's [StartupResult].
 *
 * The percentiles come from a linear-interpolation percentile over the measured launches — the same
 * estimator the macrobenchmark library uses — so a single slow launch moves p99 without dragging
 * the median. The raw samples are carried alongside, in measurement order.
 */
fun startupResult(measurement: StartupMeasurement): StartupResult {
    require(measurement.timeToFirstContentMillis.isNotEmpty()) { "at least one startup sample is required" }

    val sorted = measurement.timeToFirstContentMillis.sorted()
    return StartupResult(
        scenario = measurement.scenario,
        timeToFirstContentP50Ms = percentile(sorted, 50),
        timeToFirstContentP90Ms = percentile(sorted, 90),
        timeToFirstContentP99Ms = percentile(sorted, 99),
        samplesMs = measurement.timeToFirstContentMillis,
    )
}
