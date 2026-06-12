package net.sigmabeta.chipbox.player.resampler

/**
 * Diagnostic snapshot of a sink's [Resampler] stage, surfaced for the debug PlaybackStatus screen
 * via [net.sigmabeta.chipbox.player.speaker.SpeakerDebugInfo]. Purely observational.
 *
 * A sink that isn't currently resampling in-app (OS mode, or input rate == output rate) reports
 * `active = false`.
 */
data class ResamplerDebugInfo(
    /** User-selected resampler mode driving the sink (e.g. OS / LINEAR / CUBIC). */
    val mode: String,
    /** True when an in-app resampler is engaged; false when bypassed (OS mode, or input == output). */
    val active: Boolean,
    /** Native input rate (Hz) of the audio currently flowing into the sink. */
    val inputRateHz: Int,
    /** Rate (Hz) the sink runs at: the resampler's target, or the native rate when bypassed. */
    val outputRateHz: Int,
)
