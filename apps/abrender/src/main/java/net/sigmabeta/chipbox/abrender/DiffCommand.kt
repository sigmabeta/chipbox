package net.sigmabeta.chipbox.abrender

import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

private const val FULL_SCALE = 32768.0
private const val DBFS_SCALE = 20.0
private const val VERDICT_COLUMN_WIDTH = 18

/** How a track's render changed between run A (baseline) and run B (candidate). */
enum class Verdict {
    IDENTICAL, // bit-for-bit equal PCM
    CHANGED, // audible difference, both produced audio
    REGRESSED_SILENT, // A had audio, B is silent
    NEW_ERROR, // A succeeded, B failed
    FIXED_ERROR, // A failed, B succeeded
    STILL_ERROR, // both failed
    ONLY_IN_A, // track missing from run B
    ONLY_IN_B, // track missing from run A
}

private class DiffRow(
    val trackKey: String,
    val game: String,
    val title: String,
    val verdict: Verdict,
    val deltaRmsDb: Double, // rmsB - rmsA (level change)
    val diffRmsDbfs: Double, // RMS of (A - B) sample-wise, dBFS; Double.NaN if not computable
    val note: String,
)

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
    val rows = (metricsA.keys + metricsB.keys).sorted().map { key ->
        diffRow(key, metricsA[key], metricsB[key], wavA, wavB)
    }

    val report = File(dirB, "diff-vs-${args.runA}.tsv")
    writeReport(report, rows)
    printSummary(args, dirA, dirB, rows, wavA, wavB, metricsA, metricsB, report)
}

@Suppress("ReturnCount") // one guard return per verdict is clearer than a nested branch tree
private fun diffRow(key: String, a: TrackMetrics?, b: TrackMetrics?, wavA: File, wavB: File): DiffRow {
    if (a == null) return DiffRow(key, b!!.game, b.title, Verdict.ONLY_IN_B, Double.NaN, Double.NaN, "")
    if (b == null) return DiffRow(key, a.game, a.title, Verdict.ONLY_IN_A, Double.NaN, Double.NaN, "")

    val delta = b.rmsDbfs - a.rmsDbfs
    fun row(verdict: Verdict, note: String, diff: Double = Double.NaN) =
        DiffRow(key, a.game, a.title, verdict, delta, diff, note)

    val aFailed = a.error.isNotEmpty()
    val bFailed = b.error.isNotEmpty()
    when {
        aFailed && bFailed -> return row(Verdict.STILL_ERROR, b.error)
        aFailed && !bFailed -> return row(Verdict.FIXED_ERROR, "was: ${a.error}")
        !aFailed && bFailed -> return row(Verdict.NEW_ERROR, b.error)
    }
    if (a.pcmHash == b.pcmHash && a.frames == b.frames) {
        return row(Verdict.IDENTICAL, "", Double.NEGATIVE_INFINITY)
    }
    if (a.rmsDbfs > Double.NEGATIVE_INFINITY && b.rmsDbfs == Double.NEGATIVE_INFINITY) {
        return row(Verdict.REGRESSED_SILENT, "B is silent")
    }

    val (diffRms, note) = sampleDiff(File(wavA, a.wav), File(wavB, b.wav))
    return row(Verdict.CHANGED, note, diffRms)
}

/** RMS of A−B over the overlapping sample range, in dBFS, plus a coverage note. */
@Suppress("ReturnCount") // guard returns for each unreadable-input case
private fun sampleDiff(fileA: File, fileB: File): Pair<Double, String> {
    val a = readWavPcm(fileA) ?: return Double.NaN to "missing wav A"
    val b = readWavPcm(fileB) ?: return Double.NaN to "missing wav B"
    if (a.sampleRate != b.sampleRate) return Double.NaN to "sample-rate ${a.sampleRate}->${b.sampleRate}"

    val n = minOf(a.samples.size, b.samples.size)
    if (n == 0) return Double.NaN to "empty"
    var sumSq = 0.0
    var maxAbs = 0
    var i = 0
    while (i < n) {
        val d = a.samples[i] - b.samples[i]
        sumSq += (d.toDouble() * d.toDouble())
        val ad = abs(d)
        if (ad > maxAbs) maxAbs = ad
        i++
    }
    val rms = sqrt(sumSq / n)
    val db = if (rms <= 0.0) Double.NEGATIVE_INFINITY else DBFS_SCALE * log10(rms / FULL_SCALE)
    val lenNote = if (a.samples.size != b.samples.size) {
        " len ${a.samples.size / 2}->${b.samples.size / 2}fr"
    } else {
        ""
    }
    return db to "maxΔ=$maxAbs$lenNote"
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

private fun db(v: Double): String = when {
    v.isNaN() -> "NaN"
    v == Double.NEGATIVE_INFINITY -> "-inf"
    v == Double.POSITIVE_INFINITY -> "+inf"
    else -> v.fixed(decimals = 2)
}

private fun signed(v: Double): String =
    if (v.isNaN()) "NaN" else String.format(java.util.Locale.ROOT, "%+.2f", v)
