package net.sigmabeta.chipbox.abrender

import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Locale-independent fixed-point format — TSV must parse back identically regardless of locale. */
fun Double.fixed(decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", this)

/** Full-scale reference for a signed 16-bit sample. dBFS = 20·log10(value / [FULL_SCALE]). */
private const val FULL_SCALE = 32768.0
private const val DBFS_SCALE = 20.0

/**
 * One track's measurement, persisted as a row in a run's `metrics.tsv`.
 *
 * [pcmHash] is an FNV-1a hash over the rendered s16 little-endian byte stream — diffing two runs
 * compares hashes first, so a bit-identical render (the expected outcome of a cosmetic/no-op change)
 * is detected instantly without re-reading the WAVs.
 */
data class TrackMetrics(
    val trackKey: String,
    val game: String,
    val title: String,
    val extension: String,
    val sampleRate: Int,
    val frames: Long,
    val rmsDbfs: Double,
    val peakDbfs: Double,
    val lufs: Double,
    val pcmHash: Long,
    val error: String,
    val wav: String,
) {
    fun toTsvRow(): String = listOf(
        trackKey, game, title, extension, sampleRate.toString(), frames.toString(),
        fmt(rmsDbfs), fmt(peakDbfs), fmt(lufs), java.lang.Long.toHexString(pcmHash), error, wav,
    ).joinToString("\t") { it.tsvSanitized() }

    companion object {
        const val TSV_HEADER =
            "trackKey\tgame\ttitle\text\tsampleRate\tframes\trmsDbfs\tpeakDbfs\tlufs\tpcmHash\terror\twav"

        @Suppress("MagicNumber") // fixed TSV column indices (see TSV_HEADER)
        fun fromTsvRow(line: String): TrackMetrics? {
            val f = line.split("\t")
            if (f.size < 12) return null
            return TrackMetrics(
                trackKey = f[0], game = f[1], title = f[2], extension = f[3],
                sampleRate = f[4].toIntOrNull() ?: 0,
                frames = f[5].toLongOrNull() ?: 0L,
                rmsDbfs = parse(f[6]), peakDbfs = parse(f[7]), lufs = parse(f[8]),
                pcmHash = java.lang.Long.parseUnsignedLong(f[9], 16),
                error = f[10], wav = f[11],
            )
        }

        private fun fmt(v: Double): String = when {
            v.isNaN() -> "NaN"
            v == Double.NEGATIVE_INFINITY -> "-inf"
            v == Double.POSITIVE_INFINITY -> "+inf"
            else -> v.fixed(decimals = 3)
        }

        private fun parse(s: String): Double = when (s) {
            "NaN" -> Double.NaN
            "-inf" -> Double.NEGATIVE_INFINITY
            "+inf" -> Double.POSITIVE_INFINITY
            else -> s.toDoubleOrNull() ?: Double.NaN
        }
    }
}

/** Reads every row of a run's `metrics.tsv`, keyed by [TrackMetrics.trackKey]. */
fun readMetrics(file: File): Map<String, TrackMetrics> {
    if (!file.exists()) return emptyMap()
    return file.useLines { lines ->
        lines.drop(1)
            .mapNotNull { if (it.isBlank()) null else TrackMetrics.fromTsvRow(it) }
            .associateBy { it.trackKey }
    }
}

/**
 * Accumulates RMS, peak, an FNV-1a content hash, and (optionally) BS.1770 loudness over a render,
 * one emulator buffer at a time. Channel-agnostic: every interleaved sample feeds the same sums,
 * which is what we want for a single overall level figure.
 */
@Suppress("MagicNumber") // s16 byte masks/shifts and the FNV-1a basis are binary-format constants
class SignalStats {
    private var sumSquares = 0.0
    private var sampleCount = 0L
    private var peak = 0
    private var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis

    fun process(buffer: ShortArray, frames: Int) {
        val samples = frames * 2 // interleaved stereo
        var i = 0
        while (i < samples) {
            val s = buffer[i].toInt()
            sumSquares += (s * s).toDouble()
            val a = abs(s)
            if (a > peak) peak = a
            // hash the little-endian s16 bytes so it matches the on-disk WAV body
            hash = (hash xor (s and 0xFF).toLong()) * FNV_PRIME
            hash = (hash xor ((s shr 8) and 0xFF).toLong()) * FNV_PRIME
            i++
        }
        sampleCount += samples
    }

    fun rmsDbfs(): Double {
        if (sampleCount == 0L) return Double.NEGATIVE_INFINITY
        val rms = sqrt(sumSquares / sampleCount)
        return if (rms <= 0.0) Double.NEGATIVE_INFINITY else DBFS_SCALE * log10(rms / FULL_SCALE)
    }

    fun peakDbfs(): Double =
        if (peak <= 0) Double.NEGATIVE_INFINITY else 20.0 * log10(peak / FULL_SCALE)

    fun pcmHash(): Long = hash

    private companion object {
        const val FNV_PRIME = 0x100000001b3L
    }
}

private fun String.tsvSanitized(): String =
    replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
