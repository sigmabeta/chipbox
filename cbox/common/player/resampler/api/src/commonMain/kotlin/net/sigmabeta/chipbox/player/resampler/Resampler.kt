package net.sigmabeta.chipbox.player.resampler

/**
 * Streaming sample-rate converter for interleaved stereo 16-bit PCM. Each concrete [Resampler]
 * implements exactly one interpolation technique ([LinearResampler], [CubicResampler], …); a sink
 * injects the one the user picked rather than naming a kernel or a quality enum.
 *
 * An instance is **stateful**: the fractional read position and the per-channel history needed by
 * the kernel persist across [process] calls, so a tone that spans two buffers comes out seamless.
 * The `(inputRate, outputRate)` pair is passed in per call rather than fixed at construction — a
 * change between calls is treated as a stream discontinuity and the state is dropped, exactly as a
 * seek would. One instance drives one continuous stream; it is **not** thread-safe — the owning sink
 * drives it from a single thread.
 *
 * Why this exists: handing the OS sink a non-standard rate (e.g. an N64 rip's 32006 Hz) makes the
 * platform mixer take its arbitrary-ratio resampler path, which underruns a minimal sink buffer.
 * Resampling to the device's output rate in our pipeline keeps the sink on a clean, standard rate.
 * It's emulator-agnostic — it sees only PCM and rates — so every fixed-rate sink (Android
 * `AudioTrack`, the JVM `SourceDataLine`, the web `AudioWorklet`) can share one tested kernel.
 */
interface Resampler {
    /**
     * Resample [inputFrames] interleaved-stereo frames from [input] (at [inputRate]) into [output]
     * (interleaved stereo at [outputRate]); returns the number of output frames written. Size
     * [output] with [maxOutputFrames]. The output-frame count per call varies with the rate ratio
     * and the carried fractional phase. A change in either rate since the last call resets the
     * carried state first.
     */
    fun process(input: ShortArray, inputFrames: Int, inputRate: Int, outputRate: Int, output: ShortArray): Int

    /** A safe upper bound on the output frames [process] can emit for [inputFrames] of input at the
     *  given rates, so callers can size the output buffer once. */
    fun maxOutputFrames(inputFrames: Int, inputRate: Int, outputRate: Int): Int

    /** Drop the phase + history at a stream discontinuity (seek, track change, flush) so the next
     *  [process] starts cleanly rather than interpolating across the gap. */
    fun reset()
}
