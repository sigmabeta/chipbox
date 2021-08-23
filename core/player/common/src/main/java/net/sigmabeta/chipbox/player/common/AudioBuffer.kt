package net.sigmabeta.chipbox.player.common

data class AudioBuffer(
    val timestampMillis: Long,
    val timestampFrame: Long,
    val audio: ShortArray
)