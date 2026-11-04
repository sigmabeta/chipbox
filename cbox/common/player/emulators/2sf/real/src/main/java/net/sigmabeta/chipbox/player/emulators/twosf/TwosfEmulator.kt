package net.sigmabeta.chipbox.player.emulators.twosf

import net.sigmabeta.chipbox.player.emulators.Emulator

object TwosfEmulator : Emulator() {
    /** vio2sf's fixed NDS output rate; `get_sample_rate()` returns this for every track. */
    private const val FIXED_SAMPLE_RATE = 44_100

    /**
     * The vio2sf core renders NDS audio at a fixed 44.1 kHz — its `get_sample_rate()` returns
     * 44_100 for every track. Advertising it statically lets the cache answer a complete warm hit
     * without loading a track into the native core or tearing it down again.
     */
    override val knownSampleRate: Int = FIXED_SAMPLE_RATE

    override fun loadNativeLib() {
        System.loadLibrary("twosf")
    }

    override val supportedFileExtensions = listOf(
        "2sf",
        "mini2sf",
    )

    external override fun loadTrackInternal(path: String)

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int
}
