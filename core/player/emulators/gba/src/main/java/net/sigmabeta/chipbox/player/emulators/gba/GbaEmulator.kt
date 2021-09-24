package net.sigmabeta.chipbox.player.emulators.gba

import net.sigmabeta.chipbox.player.emulators.Emulator

object GbaEmulator : Emulator() {
    override fun loadNativeLib() {
        System.loadLibrary("gba")
    }

    override val supportedFileExtensions = listOf(
        "gsf",
        "minigsf"
    )

    external override fun loadTrackInternal(path: String)

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int
}