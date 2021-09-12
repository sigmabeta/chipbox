package net.sigmabeta.chipbox.player.emulators.gme

import net.sigmabeta.chipbox.player.emulators.Emulator

object GmeEmulator : Emulator() {
    init {
        System.loadLibrary("gme")
    }

    external override fun loadTrackInternal(path: String)

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int

    override val supportedFileExtensions = listOf(
        "gbs",
        "nsf",
        "nsfe",
        "spc"
    )
}