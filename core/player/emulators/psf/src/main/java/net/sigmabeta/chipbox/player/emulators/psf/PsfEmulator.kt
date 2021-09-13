package net.sigmabeta.chipbox.player.emulators.psf

import net.sigmabeta.chipbox.player.emulators.Emulator

object PsfEmulator : Emulator() {
    init {
        System.loadLibrary("aopsf")
    }

    override val supportedFileExtensions = listOf(
        "psf",
        "minipsf",
        "psf2",
        "minipsf2"
    )

    external override fun loadTrackInternal(path: String)

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int
}