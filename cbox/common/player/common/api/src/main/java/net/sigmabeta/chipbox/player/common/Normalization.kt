package net.sigmabeta.chipbox.player.common

// Peak-normalization math, shared by the loudness log (the figure we *report*) and the
// speaker's [VolumeProcessor] (the gain we *apply*) so the two can never drift apart.

/** Loudest 16-bit sample a normalized track is aimed at: 85% of full scale, leaving a little
 *  headroom so the fade ramp, resampler, and inter-sample peaks don't clip. */
const val NORMALIZATION_TARGET_FRACTION: Double = 0.85

/**
 * Gain that brings a track whose loudest sample magnitude is [peakAmplitude] to
 * [NORMALIZATION_TARGET_FRACTION] of full scale. Returns `1.0` (unchanged) when the peak is
 * unknown ([peakAmplitude] `<= 0`) — e.g. a first-time render that hasn't finished measuring,
 * or a live/uncached source that never measures at all.
 */
fun normalizationGain(peakAmplitude: Int): Double {
    if (peakAmplitude <= 0) return 1.0
    val fullScale = Short.MAX_VALUE.toInt()
    return NORMALIZATION_TARGET_FRACTION * fullScale / peakAmplitude
}
