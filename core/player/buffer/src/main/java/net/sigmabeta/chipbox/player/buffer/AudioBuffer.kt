package net.sigmabeta.chipbox.player.buffer

data class AudioBuffer(
    val trackId: Long,
    val sampleRate: Int,
    val data: ShortArray
)