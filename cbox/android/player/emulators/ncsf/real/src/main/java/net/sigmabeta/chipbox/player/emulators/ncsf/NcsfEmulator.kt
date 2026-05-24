package net.sigmabeta.chipbox.player.emulators.ncsf

import net.sigmabeta.chipbox.player.emulators.Emulator

object NcsfEmulator : Emulator() {
    override fun loadNativeLib() {
        System.loadLibrary("ncsf")
    }

    override val supportedFileExtensions = listOf(
        "ncsf",
        "minincsf",
    )

    external override fun loadTrackInternal(path: String)

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int
}
