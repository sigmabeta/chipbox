package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.util.Locale
import kotlinx.serialization.SerializationException

private const val SCENARIO_WIDTH = 12
private const val COLUMN_WIDTH = 10
private const val DECIMALS = 2

/**
 * `diff` — compare every axis of two runs. Run A is the baseline, run B the current run. Prints the
 * per-axis, per-scenario ratio and the geometric mean (per axis and overall), or refuses (with the
 * reason) when the runs are not comparable. A refusal is not an error: the tool declines rather than
 * report a wrong number.
 *
 * A run whose `results.json` cannot be read as v1.1 (e.g. a v1 baseline, whose audio results have no
 * `path`) is refused the same way — it is incomparable, not a crash.
 */
fun runDiff(args: DiffArgs) {
    val baseline = readOrRefuse(args.baseline) ?: return
    val current = readOrRefuse(args.current) ?: return

    when (val outcome = compareRuns(baseline, current)) {
        is DiffOutcome.Refused -> println("Refusing to compare: ${outcome.reason.message}")
        is DiffOutcome.Compared -> printReport(args, outcome.report)
    }
}

/** The result of reading a run's file: the decoded document, or why it is not v1.1-compatible. */
internal sealed interface ResultRead {
    data class Decoded(val result: PerfResult) : ResultRead
    data class Incompatible(val file: File, val reason: String) : ResultRead
}

/** Reads a run's file, turning an undecodable (e.g. v1) result into a refusal instead of an exception. */
internal fun readResult(file: File): ResultRead =
    try {
        ResultRead.Decoded(PerfbenchJson.decodeFromString(file.readText()))
    } catch (e: SerializationException) {
        ResultRead.Incompatible(file, e.message ?: "unreadable perfbench result")
    }

/** The line `diff` prints when a run's file is not a v1.1-compatible result. */
internal fun refusalLine(read: ResultRead.Incompatible): String =
    "Refusing to compare: ${read.file.absolutePath} is not a v1.1-compatible perfbench result — " +
        "re-record the baseline (${read.reason})"

/** Reads a run, printing the refusal line and returning `null` when the file is not v1.1-compatible. */
private fun readOrRefuse(file: File): PerfResult? = when (val read = readResult(file)) {
    is ResultRead.Decoded -> read.result

    is ResultRead.Incompatible -> {
        println(refusalLine(read))
        null
    }
}

private fun printReport(args: DiffArgs, report: DiffReport) {
    println("Diff (A = baseline, B = current)")
    println("  A: ${args.baseline.absolutePath}")
    println("  B: ${args.current.absolutePath}")
    report.axes.forEach(::printAxis)
    println()
    println("overall geometric mean: ${decimal(report.geometricMean)}x")
}

private fun printAxis(axis: AxisComparison) {
    val width = maxOf(SCENARIO_WIDTH, axis.scenarios.maxOf { it.scenario.length })
    println()
    println("${axis.axis} — ${axis.metricLabel}; ratio = A ÷ B, above 1 means B is better")
    println(
        "  ${"scenario".padEnd(width)}${"baseline".padStart(COLUMN_WIDTH)}" +
            "${"current".padStart(COLUMN_WIDTH)}${"ratio".padStart(COLUMN_WIDTH)}",
    )
    axis.scenarios.forEach { scenario ->
        println(
            "  ${scenario.scenario.padEnd(width)}${decimal(scenario.baselineMetric).padStart(COLUMN_WIDTH)}" +
                "${decimal(scenario.currentMetric).padStart(COLUMN_WIDTH)}" +
                "${"${decimal(scenario.ratio)}x".padStart(COLUMN_WIDTH)}",
        )
    }
    println("  geometric mean: ${decimal(axis.geometricMean)}x")
}

private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.${DECIMALS}f", value)
