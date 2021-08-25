package net.sigmabeta.chipbox.player.common

data class AudioBuffer(
    val bufferNumber: Int,
    val timestampMillis: Double,
    val timestampFrame: Long,
    val sampleRate: Int,
    val data: ShortArray
)