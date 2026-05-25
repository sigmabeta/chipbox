package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.player.common.NORMALIZATION_TARGET_LUFS
import net.sigmabeta.chipbox.player.common.NORMALIZATION_TRUE_PEAK_CEILING_DBTP
import net.sigmabeta.chipbox.player.common.normalizationGain
import net.sigmabeta.chipbox.utils.formatDecimal
import net.sigmabeta.sage.logging.Hatchet

/**
 * Emits the one-line loudness / true-peak / gain report shared by the two cache sources:
 * [CachingPcmSource] logs it the moment a render finishes (right after the cache-write-complete
 * line), and [CachedFilePcmSource] logs it on open from the values stashed in the file header,
 * so a cached replay reports the same figure without re-measuring.
 */
internal object LoudnessLog {
    /**
     * Log [trackTitle]'s integrated loudness [loudnessLufs] and true peak [truePeakDbtp] plus
     * the gain — the exact value the speaker's
     * [net.sigmabeta.chipbox.player.common.VolumeProcessor] applies — that brings it to
     * [NORMALIZATION_TARGET_LUFS] without exceeding [NORMALIZATION_TRUE_PEAK_CEILING_DBTP].
     */
    fun report(hatchet: Hatchet, trackTitle: String, loudnessLufs: Double, truePeakDbtp: Double) {
        if (!loudnessLufs.isFinite() || loudnessLufs > 0.0) {
            hatchet.i("Track $trackTitle: no usable loudness measurement.")
            return
        }
        val gain = normalizationGain(loudnessLufs, truePeakDbtp)
        val peakField = if (truePeakDbtp.isFinite()) {
            "${formatDecimal(truePeakDbtp, 1)} dBTP"
        } else {
            "—"
        }
        hatchet.i(
            "Track $trackTitle: ${formatDecimal(loudnessLufs, 1)} LUFS, $peakField. " +
                "Multiply by ${formatDecimal(gain, 3)}x to reach " +
                "${formatDecimal(NORMALIZATION_TARGET_LUFS, 0)} LUFS."
        )
    }
}
