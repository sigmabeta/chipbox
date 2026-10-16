package net.sigmabeta.chipbox.abrender

import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

private const val FULL_SCALE = 32768.0
private const val DBFS_SCALE = 20.0

/**
 * How a track's render changed between run A (baseline) and run B (candidate).
 *
 * - [IDENTICAL]   bit-for-bit equal PCM (hashes and frame counts match)
 * - [CHANGED]     both produced audio, but it differs
 * - [REGRESSED_SILENT] A had audio, B is silent
 * - [NEW_ERROR]   A rendered, B failed
 * - [FIXED_ERROR] A failed, B rendered
 * - [STILL_ERROR] both failed
 * - [ONLY_IN_A] / [ONLY_IN_B] track present in only one run (corpus/args mismatch)
 */
enum class Verdict {
    IDENTICAL,
    CHANGED,
    REGRESSED_SILENT,
    NEW_ERROR,
    FIXED_ERROR,
    STILL_ERROR,
    ONLY_IN_A,
    ONLY_IN_B,
}

/** One track's verdict between two render runs. */
data class DiffRow(
    val trackKey: String,
    val game: String,
    val title: String,
    val verdict: Verdict,
    /** rmsB − rmsA (level change), in dB. */
    val deltaRmsDb: Double,
    /** RMS of (A − B) sample-wise, in dBFS; [Double.NaN] if not computable. */
    val diffRmsDbfs: Double,
    val note: String,
)

/**
 * Compares two render runs produced by [runRender], returning one [DiffRow] per track in the union
 * of both runs, sorted by track key.
 *
 * This is the single source of truth for "did two runs produce the same PCM?", shared by the
 * desktop `abrender diff` command and the bit-identity golden checks (host JVM and on-device).
 * PCM hashes are checked first, so a bit-identical render — the expected outcome of a cosmetic or
 * no-op change — is classified instantly without re-reading the WAVs. Only [Verdict.CHANGED] tracks
 * touch the WAVs, for a sample-wise diff.
 */
fun diffRuns(
    metricsA: Map<String, TrackMetrics>,
    metricsB: Map<String, TrackMetrics>,
    wavA: File,
    wavB: File,
): List<DiffRow> =
    (metricsA.keys + metricsB.keys).sorted().map { key ->
        diffRow(key, metricsA[key], metricsB[key], wavA, wavB)
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
