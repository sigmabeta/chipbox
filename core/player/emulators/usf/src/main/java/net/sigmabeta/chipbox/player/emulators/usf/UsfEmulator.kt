package net.sigmabeta.chipbox.player.emulators.usf

import net.sigmabeta.chipbox.player.emulators.Emulator

object UsfEmulator : Emulator() {
    init {
        System.loadLibrary("usf")
    }

    override val supportedFileExtensions = listOf(
        "usf",
        "miniusf"
    )

    external override fun loadTrackInternal(path: String)

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int
}