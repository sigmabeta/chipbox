package net.sigmabeta.chipbox.player.emulators.gba

import net.sigmabeta.chipbox.player.emulators.Emulator

object GbaEmulator : Emulator() {
    /**
     * 2: output is the GSF driver's own settled rate (32768 or 65536 Hz) instead of a resample to
     * 44100 Hz. Keep in step with `WasmGbaEmulator`, which runs the same `Gba.cpp`.
     */
    override val renderVersion: Int = 2

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
