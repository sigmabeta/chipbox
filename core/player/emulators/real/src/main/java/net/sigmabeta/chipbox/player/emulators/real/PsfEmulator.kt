package net.sigmabeta.chipbox.player.emulators.real

import net.sigmabeta.chipbox.player.emulators.Emulator

object PsfEmulator : Emulator() {
    init {
        System.loadLibrary("aopsf")
    }

    override var sampleRate = 44100

    external override fun loadTrackInternal(path: String)

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?
}