package net.sigmabeta.chipbox.player.common

/**
 * Streaming sample-rate converter for interleaved stereo 16-bit PCM, from [inputRate] to a fixed
 * [outputRate]. It is **stateful**: the fractional read position and the per-channel history needed
 * by the interpolation kernel persist across [process] calls, so a tone that spans two buffers comes
 * out seamless. One instance drives one continuous stream at one (in, out) pair; it is **not**
 * thread-safe — the owning sink drives it from a single thread.
 *
 * Why this exists: handing the OS sink a non-standard rate (e.g. an N64 rip's 32006 Hz) makes the
 * platform mixer take its arbitrary-ratio resampler path, which underruns a minimal AudioTrack
 * buffer. Resampling to the device's output rate in our pipeline keeps the sink on a clean, standard
 * rate. It's emulator-agnostic — it sees only PCM and rates — so every sink that runs at a fixed
 * rate (Android `AudioTrack`, the web `AudioWorklet`) can share one tested implementation.
 */
interface Resampler {
    val inputRate: Int
    val outputRate: Int

    /**
     * Resample [inputFrames] interleaved-stereo frames from [input] into [output] (interleaved
     * stereo at [outputRate]); returns the number of output frames written. Size [output] with
     * [maxOutputFrames]. The number of output frames per call varies with the rate ratio and the
     * carried fractional phase.
     */
    fun process(input: ShortArray, inputFrames: Int, output: ShortArray): Int

    /** A safe upper bound on the output frames [process] can emit for [inputFrames] of input, so
     *  callers can size the output buffer once. */
    fun maxOutputFrames(inputFrames: Int): Int

    /** Drop the phase + history at a stream discontinuity (seek, track change, flush) so the next
     *  [process] starts cleanly rather than interpolating across the gap. */
    fun reset()
}

/**
 * Interpolation kernel used by [DefaultResamplerFactory]. Add new kernels here (e.g. a windowed-sinc
 * `SINC`) and a matching branch in the factory — the [Resampler] contract and every call site stay
 * unchanged.
 */
enum class ResamplerQuality {
    /** 2-point linear. Cheapest; what the web worklet uses today — fine for band-limited chiptune. */
    LINEAR,

    /** 4-point Catmull-Rom cubic. A small quality bump over linear at a few extra mults/sample. */
    CUBIC,
}

/** Constructs [Resampler]s; the single place an algorithm is chosen, so sinks never name one. */
interface ResamplerFactory {
    /** False when in == out, so callers can skip resampling (and the bypass costs nothing). */
    fun needed(inputRate: Int, outputRate: Int): Boolean = inputRate != outputRate

    /** A streaming converter for the given rates using this factory's configured [ResamplerQuality]. */
    fun create(inputRate: Int, outputRate: Int): Resampler
}

/** Builds a [Resampler] of the given [quality]. Swap [quality] (DI, or a Settings toggle for on-device
 *  A/B) to change every sink's algorithm in one place. */
class DefaultResamplerFactory(private val quality: ResamplerQuality) : ResamplerFactory {
    override fun create(inputRate: Int, outputRate: Int): Resampler = when (quality) {
        ResamplerQuality.LINEAR -> LinearResampler(inputRate, outputRate)
        ResamplerQuality.CUBIC -> CubicResampler(inputRate, outputRate)
    }
}
