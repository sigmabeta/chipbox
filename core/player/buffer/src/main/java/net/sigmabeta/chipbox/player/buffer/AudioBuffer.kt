package net.sigmabeta.chipbox.player.buffer

data class AudioBuffer(
        val sampleRate: Int,
        val data: ShortArray
)