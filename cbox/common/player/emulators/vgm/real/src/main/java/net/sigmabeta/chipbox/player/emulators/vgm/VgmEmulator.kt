package net.sigmabeta.chipbox.player.emulators.vgm

import net.sigmabeta.chipbox.player.emulators.Emulator

object VgmEmulator : Emulator() {
    override fun loadNativeLib() {
        System.loadLibrary("vgm")
    }

    override val supportedFileExtensions = listOf(
        "vgm",
        "vgz"
    )

    override fun loadTrackInternal(path: String) {
        hatchet.d("Starting VGM track from $path")
        loadTrackInternalNative(path)
    }

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int

    private external fun loadTrackInternalNative(path: String)
}
