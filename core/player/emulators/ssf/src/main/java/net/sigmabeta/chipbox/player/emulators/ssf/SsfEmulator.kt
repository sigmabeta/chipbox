package net.sigmabeta.chipbox.player.emulators.ssf

import net.sigmabeta.chipbox.player.emulators.Emulator

object SsfEmulator : Emulator() {
    override fun loadNativeLib() {
        System.loadLibrary("ssf")
    }

    override val supportedFileExtensions = listOf(
        "ssf",
        "minissf",
        "dsf",
        "minidsf",
    )

    external override fun loadTrackInternal(path: String)

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int
}