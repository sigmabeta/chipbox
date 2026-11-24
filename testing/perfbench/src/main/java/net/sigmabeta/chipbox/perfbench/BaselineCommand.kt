package net.sigmabeta.chipbox.perfbench

import java.util.Locale
import kotlin.system.exitProcess
import kotlinx.serialization.encodeToString

/**
 * `baseline` merges several recordings into one baseline document: per scenario, the recording
 * whose primary metric is the median (see [medianBaseline]). It writes the result to `--out` and
 * prints each scenario's spread, so a noisy scenario shows up when the baseline is recorded.
 *
 * Unreadable or mutually incomparable recordings exit non-zero without writing anything. A
 * baseline is what every later run is gated against, so a partial or mixed one is worse than
 * none.
 */
fun runBaseline(args: BaselineArgs) {
    val recordings = args.recordings.map { file ->
        when (val read = readResult(file)) {
            is ResultRead.Decoded -> read.result
            is ResultRead.Incompatible -> refuse(refusalLine(read))
        }
    }
    val merged = when (val outcome = medianBaseline(recordings)) {
        is BaselineOutcome.Refused -> refuse("Refusing to merge the recordings: ${outcome.reason.message}")
        is BaselineOutcome.Merged -> outcome
    }

    args.outFile.absoluteFile.parentFile?.mkdirs()
    args.outFile.writeText(PerfbenchJson.encodeToString(merged.result))

    println("Baseline: median of ${recordings.size} recording(s) → ${args.outFile.absolutePath}")
    merged.spreads.groupBy { it.axis to it.metricLabel }.forEach { (axis, spreads) ->
        println("  ${axis.first} — ${axis.second}: min / median / max (spread)")
        spreads.forEach { println("    ${it.scenario}  ${spreadLine(it.values.filterNotNull())}") }
    }
}

/** `min / median / max (spread %)` for one scenario's values across the recordings. */
internal fun spreadLine(values: List<Double>): String {
    val sorted = values.sorted()
    val median = sorted[(sorted.size - 1) / 2]
    val spread = if (median > 0.0) (sorted.last() - sorted.first()) / median * PERCENT else 0.0
    return String.format(
        Locale.ROOT,
        "%.2f / %.2f / %.2f (%.1f%%)",
        sorted.first(),
        median,
        sorted.last(),
        spread,
    )
}

private const val PERCENT = 100.0

private fun refuse(message: String): Nothing {
    println(message)
    exitProcess(1)
}
