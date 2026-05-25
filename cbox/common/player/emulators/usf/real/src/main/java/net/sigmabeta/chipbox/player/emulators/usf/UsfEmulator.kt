package net.sigmabeta.chipbox.player.emulators.usf

import net.sigmabeta.chipbox.player.emulators.Emulator

object UsfEmulator : Emulator() {
    override fun loadNativeLib() {
        System.loadLibrary("usf")
    }

    override val supportedFileExtensions = listOf(
        "usf",
        "miniusf"
    )

    override fun loadTrackInternal(path: String) {
        hatchet.d("Starting USF track from $path")
        loadTrackInternalNative(path)
    }

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int

    private external fun loadTrackInternalNative(path: String)
}
