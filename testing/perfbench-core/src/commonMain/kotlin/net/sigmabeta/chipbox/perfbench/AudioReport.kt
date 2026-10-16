package net.sigmabeta.chipbox.perfbench

/**
 * The raw timings for one corpus file: the frames produced, the sample rate, and the per-repeat
 * wall-clock and CPU seconds. Turning this into an [AudioResult] is pure, so the report math is
 * unit-tested without a render. [cpuSeconds] may be empty when the JVM reports no thread CPU time.
 */
data class AudioMeasurement(
    val path: String,
    val frames: Long,
    val sampleRate: Int,
    val wallSeconds: List<Double>,
    val cpuSeconds: List<Double>,
)

/**
 * Assembles one file's [AudioResult].
 *
 * Real-time factor is audio duration ÷ wall-clock time; the headline [AudioResult.rtf] uses the
 * median wall time so a single noisy repeat cannot dominate. CPU seconds are reported as their own
 * median, not mixed into the RTF, and are `null` when no CPU samples were collected.
 */
fun audioResult(measurement: AudioMeasurement): AudioResult {
    require(measurement.wallSeconds.isNotEmpty()) { "at least one timed repeat is required" }
    require(measurement.sampleRate > 0) { "sample rate must be positive, was ${measurement.sampleRate}" }

    val durationSeconds = measurement.frames.toDouble() / measurement.sampleRate
    val wallMedian = median(measurement.wallSeconds)
    return AudioResult(
        path = measurement.path,
        rtf = durationSeconds / wallMedian,
        cpuSeconds = measurement.cpuSeconds.takeIf { it.isNotEmpty() }?.let(::median),
        median = wallMedian,
        min = measurement.wallSeconds.min(),
        max = measurement.wallSeconds.max(),
        samples = measurement.wallSeconds,
    )
}

/**
 * One file's [AudioResult] for a render that failed: it carries [error] and has no real-time factor
 * or wall-clock samples. Failures are results, not omissions, so an unattended run never silently
 * shrinks; the diff skips these entries.
 */
fun erroredAudioResult(path: String, error: String): AudioResult = AudioResult(path = path, error = error)

/** Median of [values] (mean of the two middle elements for an even count). */
fun median(values: List<Double>): Double {
    require(values.isNotEmpty()) { "median of an empty list is undefined" }
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
}
