package net.sigmabeta.chipbox.player.emulators.twosf

import net.sigmabeta.chipbox.player.emulators.Emulator

object TwosfEmulator : Emulator() {
    init {
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