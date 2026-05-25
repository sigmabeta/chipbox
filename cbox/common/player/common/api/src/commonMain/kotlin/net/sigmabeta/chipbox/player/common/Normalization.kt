package net.sigmabeta.chipbox.player.common

import kotlin.math.min
import kotlin.math.pow

// Loudness-normalization math, shared by the loudness log (the figures we *report*) and the
// speaker's [VolumeProcessor] (the gain we *apply*) so the two can never drift apart.

/** LUFS we aim for. -14 matches what most streaming platforms (Spotify, YouTube) target. */
const val NORMALIZATION_TARGET_LUFS: Double = -14.0

/** Inter-sample peak ceiling. The loudness gain is clamped so no true-peak in the post-gain
 *  signal climbs above this dBTP value — leaves a tiny margin under full scale so the fade
 *  ramp, resampler, and downstream DAC can't push past 0 dBFS. */
const val NORMALIZATION_TRUE_PEAK_CEILING_DBTP: Double = -1.0

/** dB-to-linear conversion factor for amplitude (true peak is an amplitude quantity). */
private const val DBFS_VOLTAGE_FACTOR: Double = 20.0

/**
 * Gain that brings a track of measured integrated loudness [loudnessLufs] (BS.1770 LUFS) up to
 * [NORMALIZATION_TARGET_LUFS], clamped so its true-peak doesn't exceed
 * [NORMALIZATION_TRUE_PEAK_CEILING_DBTP] post-gain. Returns `1.0` (unchanged) when the
 * measurement is unavailable — non-finite [loudnessLufs] (e.g. `Double.NaN` for a first-time
 * render that hasn't measured a 400 ms block yet) or a stray positive value (LUFS is always
 * `<= 0`; a positive reading is interpretable only as a corrupt header field).
 */
fun normalizationGain(loudnessLufs: Double, truePeakDbtp: Double): Double {
    if (!loudnessLufs.isFinite() || loudnessLufs > 0.0) return 1.0

    val loudnessGain = 10.0.pow((NORMALIZATION_TARGET_LUFS - loudnessLufs) / DBFS_VOLTAGE_FACTOR)
    // No measured peak ⇒ no ceiling: an infinite cap leaves the loudness gain untouched by min().
    val peakCap = if (truePeakDbtp.isFinite()) {
        10.0.pow((NORMALIZATION_TRUE_PEAK_CEILING_DBTP - truePeakDbtp) / DBFS_VOLTAGE_FACTOR)
    } else {
        Double.POSITIVE_INFINITY
    }
    return min(loudnessGain, peakCap)
}
