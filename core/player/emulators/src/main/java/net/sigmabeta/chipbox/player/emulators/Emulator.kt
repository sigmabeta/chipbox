package net.sigmabeta.chipbox.player.emulators

import net.sigmabeta.chipbox.models.Track

interface Emulator {
    var sampleRate: Int

    var trackOver: Boolean

    fun loadTrack(track: Track)

    fun generateBuffer(buffer: ShortArray): Int

    fun teardown()
}