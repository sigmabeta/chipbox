package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.util.Locale

private const val SCENARIO_WIDTH = 12
private const val COLUMN_WIDTH = 10
private const val DECIMALS = 2

/**
 * `diff` — compare the audio axis of two runs. Run A is the baseline, run B the current run. Prints
 * the per-scenario speedup and its geometric mean, or refuses (with the reason) when the runs are
 * not comparable. A refusal is not an error: the tool declines rather than report a wrong number.
 */
fun runDiff(args: DiffArgs) {
    val baseline = readResult(args.baseline)
    val current = readResult(args.current)

    when (val outcome = compareAudioRuns(baseline, current)) {
        is DiffOutcome.Refused -> println("Refusing to compare: ${outcome.reason.message}")
        is DiffOutcome.Compared -> printReport(args, outcome.report)
    }
}

private fun readResult(file: File): PerfResult = PerfbenchJson.decodeFromString(file.readText())

private fun printReport(args: DiffArgs, report: DiffReport) {
    println("Audio diff (A = baseline, B = current)")
    println("  A: ${args.baseline.absolutePath}")
    println("  B: ${args.current.absolutePath}")
    println()
    println(
        "  ${"scenario".padEnd(SCENARIO_WIDTH)}${"baseline".padStart(COLUMN_WIDTH)}" +
            "${"current".padStart(COLUMN_WIDTH)}${"speedup".padStart(COLUMN_WIDTH)}",
    )
    report.scenarios.forEach { speedup ->
        println(
            "  ${speedup.scenario.padEnd(SCENARIO_WIDTH)}${decimal(speedup.baselineRtf).padStart(COLUMN_WIDTH)}" +
                "${decimal(speedup.currentRtf).padStart(COLUMN_WIDTH)}" +
                "${"${decimal(speedup.speedup)}x".padStart(COLUMN_WIDTH)}",
        )
    }
    println()
    println("  RTF baseline -> current; a speedup above 1 means B rendered the same audio faster")
    println("  geometric-mean speedup: ${decimal(report.geometricMean)}x")
}

private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.${DECIMALS}f", value)
