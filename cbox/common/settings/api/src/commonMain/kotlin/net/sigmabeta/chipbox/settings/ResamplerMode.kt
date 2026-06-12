package net.sigmabeta.chipbox.settings

/**
 * How the player converts an emulator's native sample rate to the audio device's output rate.
 *
 * [OS] hands the platform sink the native rate and lets the OS mixer resample (the pre-resampler
 * behaviour) — simplest, but on some devices a non-standard rate (e.g. an N64 rip's 32006 Hz) takes
 * the mixer's arbitrary-ratio path and glitches on a minimal buffer. [LINEAR] and [CUBIC] resample
 * in-app to the device rate with the respective kernel, keeping the sink on a clean standard rate.
 */
enum class ResamplerMode {
    OS,
    LINEAR,
    CUBIC,
    ;

    companion object {
        /** In-app cubic by default — the fix for the odd-rate mixer glitches. */
        val DEFAULT: ResamplerMode = CUBIC

        /** Parses a persisted [value] back into a [ResamplerMode], falling back to [DEFAULT]. */
        fun fromStorageValue(value: String?): ResamplerMode = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
