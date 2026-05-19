package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.sage.logging.Hatchet
import kotlin.math.log10

/**
 * Emits the one-line peak-loudness / headroom report shared by the two cache sources:
 * [CachingPcmSource] logs it the moment a render finishes (right after the cache-write-complete
 * line), and [CachedFilePcmSource] logs it on open from the value stashed in the file header,
 * so a cached replay reports the same figure without re-measuring.
 */
internal object LoudnessLog {
    /** Headroom target: 95% of full scale. */
    private const val TARGET_PEAK_FRACTION = 0.95

    /** dB = 20·log10(amplitude ratio) for a voltage/sample-amplitude quantity. */
    private const val DBFS_VOLTAGE_FACTOR = 20.0

    private const val PERCENT = 100

    /**
     * Log the loudest sample seen across [trackTitle] and the gain that would bring it to
     * [TARGET_PEAK_FRACTION] of full scale (headroom info for a future normalization pass).
     */
    fun report(hatchet: Hatchet, trackTitle: String, peakAmplitude: Int) {
        val fullScale = Short.MAX_VALUE.toInt()
        if (peakAmplitude <= 0) {
            hatchet.i("Track $trackTitle: no audible samples; peak loudness unavailable.")
            return
        }
        val targetAmplitude = TARGET_PEAK_FRACTION * fullScale
        val gain = targetAmplitude / peakAmplitude
        val dbfs = DBFS_VOLTAGE_FACTOR * log10(peakAmplitude.toDouble() / fullScale)
        hatchet.i(
            "Track $trackTitle: peak amplitude $peakAmplitude/$fullScale " +
                "(${"%.1f".format(dbfs)} dBFS). Multiply by ${"%.3f".format(gain)}x to reach " +
                "${(TARGET_PEAK_FRACTION * PERCENT).toInt()}% of full scale."
        )
    }
}
