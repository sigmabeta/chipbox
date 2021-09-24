package net.sigmabeta.chipbox.player.emulators.gme

import net.sigmabeta.chipbox.player.emulators.Emulator

object GmeEmulator : Emulator() {
    override fun loadNativeLib() {
        System.loadLibrary("gme")
    }

    override val supportedFileExtensions = listOf(
        "gbs",
        "nsf",
        "nsfe",
        "spc"
    )

    private var trackNumber: Int = 0

    override fun setTrackNumber(number: Int) {
        this.trackNumber = number
    }

    override fun loadTrackInternal(path: String) = loadTrackInternalWithNumber(path, trackNumber)

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int

    private external fun loadTrackInternalWithNumber(path: String, trackNumber: Int)
}