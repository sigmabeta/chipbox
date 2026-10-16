package net.sigmabeta.chipbox.abrender

import java.io.File
import kotlin.system.exitProcess

private const val VERDICT_COLUMN_WIDTH = 18

/**
 * `diff` — compare two render runs produced by [runRender]. The headline metric is the RMS of the
 * sample-wise difference A−B (dBFS): −inf means bit-identical, higher means more divergence. PCM
 * hashes are checked first, so an unchanged render is classified instantly without re-reading WAVs.
 * Aggregate ΔRMS (per the team's usual metric) is reported alongside.
 */
fun runDiff(args: Args) {
    val dirA = resolveRunDir(args.outDir, args.runA!!)
    val dirB = resolveRunDir(args.outDir, args.runB!!)
    val metricsA = readMetrics(File(dirA, "metrics.tsv"))
    val metricsB = readMetrics(File(dirB, "metrics.tsv"))

    if (metricsA.isEmpty()) {
        println("No metrics in run A: $dirA")
        return
    }
    if (metricsB.isEmpty()) {
        println("No metrics in run B: $dirB")
        return
    }

    val wavA = File(dirA, "wav")
    val wavB = File(dirB, "wav")
    val rows = diffRuns(metricsA, metricsB, wavA, wavB)

    val report = File(dirB, "diff-vs-${reportSlug(args.runA!!)}.tsv")
    writeReport(report, rows)
    printSummary(args, dirA, dirB, rows, wavA, wavB, metricsA, metricsB, report)

    // --check turns the report into a gate: the scripts' verify step fails (non-zero) on any track
    // that falls outside the tolerance, so it can be a shell `if`/CI step.
    if (args.check) {
        val verdict = GoldenReport(rows, toleranceOf(args.tolerance))
        println()
        println("${if (verdict.passed) "CHECK: PASS" else "CHECK: FAIL"} — ${verdict.describe()}")
        if (!verdict.passed) exitProcess(1)
    }
}

/** Maps `--tolerance` to a [GoldenTolerance]; validated at parse time, so this only sees known names. */
private fun toleranceOf(name: String?): GoldenTolerance = when (name?.lowercase()) {
    null, "strict" -> GoldenTolerance.STRICT
    "cross-isa" -> GoldenTolerance.CROSS_ISA
    else -> error("unknown --tolerance '$name'")
}

@Suppress("LongParameterList")
private fun printSummary(
    args: Args,
    dirA: File,
    dirB: File,
    rows: List<DiffRow>,
    wavA: File,
    wavB: File,
    metricsA: Map<String, TrackMetrics>,
    metricsB: Map<String, TrackMetrics>,
    report: File,
) {
    val byVerdict = rows.groupingBy { it.verdict }.eachCount()
    println("A/B diff:  A=${args.runA} ($dirA)")
    println("           B=${args.runB} ($dirB)")
    println("           ${rows.size} tracks compared")
    println()
    Verdict.entries.forEach { v -> byVerdict[v]?.let { println("  ${v.name.padEnd(VERDICT_COLUMN_WIDTH)} $it") } }
    println()

    // Regressions first — these are what a sync must not introduce.
    val regressions = rows.filter { it.verdict == Verdict.NEW_ERROR || it.verdict == Verdict.REGRESSED_SILENT }
    if (regressions.isNotEmpty()) {
        println("LIKELY REGRESSIONS (${regressions.size}):")
        regressions.forEach { println("  ! ${it.game} — ${it.title}  [${it.verdict}] ${it.note}") }
        println()
    }
    val fixed = rows.filter { it.verdict == Verdict.FIXED_ERROR }
    if (fixed.isNotEmpty()) {
        println("FIXED (${fixed.size}):")
        fixed.forEach { println("  + ${it.game} — ${it.title}  (${it.note})") }
        println()
    }

    val changed = rows.filter { it.verdict == Verdict.CHANGED }
        .sortedByDescending { if (it.diffRmsDbfs.isNaN()) Double.NEGATIVE_INFINITY else it.diffRmsDbfs }
    if (changed.isNotEmpty()) {
        val show = changed.take(args.topN)
        println("MOST CHANGED (top ${show.size} of ${changed.size}, by diff-RMS):")
        show.forEach { r ->
            val a = File(wavA, metricsA[r.trackKey]?.wav ?: "(unknown).wav").path
            val b = File(wavB, metricsB[r.trackKey]?.wav ?: "(unknown).wav").path
            println("  ~ ${r.game} — ${r.title}")
            println("      diffRMS=${db(r.diffRmsDbfs)}  ΔRMS=${signed(r.deltaRmsDb)}  ${r.note}")
            println("      A: $a")
            println("      B: $b")
        }
        println()
    }

    val identical = byVerdict[Verdict.IDENTICAL] ?: 0
    if (identical == rows.size) println("All ${rows.size} renders are BIT-IDENTICAL. ✓")
    println("Full report: $report")
}

private fun writeReport(file: File, rows: List<DiffRow>) {
    file.bufferedWriter().use { w ->
        w.write("trackKey\tgame\ttitle\tverdict\tdeltaRmsDb\tdiffRmsDbfs\tnote")
        w.newLine()
        rows.forEach { r ->
            w.write(
                listOf(r.trackKey, r.game, r.title, r.verdict.name, db(r.deltaRmsDb), db(r.diffRmsDbfs), r.note)
                .joinToString("\t") { it.replace('\t', ' ') }
            )
            w.newLine()
        }
    }
}

private fun resolveRunDir(outDir: File, runRef: String): File {
    val asPath = File(runRef)
    return if (asPath.isDirectory) asPath else File(outDir, runRef)
}

/** A filesystem-safe stem for the report name: `--a` may be a label or an absolute path. */
private fun reportSlug(runRef: String): String =
    File(runRef).name.ifBlank { "run" }.replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun db(v: Double): String = when {
    v.isNaN() -> "NaN"
    v == Double.NEGATIVE_INFINITY -> "-inf"
    v == Double.POSITIVE_INFINITY -> "+inf"
    else -> v.fixed(decimals = 2)
}

private fun signed(v: Double): String =
    if (v.isNaN()) "NaN" else String.format(java.util.Locale.ROOT, "%+.2f", v)
