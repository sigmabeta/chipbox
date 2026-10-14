package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.util.Locale

private const val SCENARIO_WIDTH = 12
private const val COLUMN_WIDTH = 10
private const val DECIMALS = 2

/**
 * `diff` — compare every axis of two runs. Run A is the baseline, run B the current run. Prints the
 * per-axis, per-scenario ratio and the geometric mean (per axis and overall), or refuses (with the
 * reason) when the runs are not comparable. A refusal is not an error: the tool declines rather than
 * report a wrong number.
 */
fun runDiff(args: DiffArgs) {
    val baseline = readResult(args.baseline)
    val current = readResult(args.current)

    when (val outcome = compareRuns(baseline, current)) {
        is DiffOutcome.Refused -> println("Refusing to compare: ${outcome.reason.message}")
        is DiffOutcome.Compared -> printReport(args, outcome.report)
    }
}

private fun readResult(file: File): PerfResult = PerfbenchJson.decodeFromString(file.readText())

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
