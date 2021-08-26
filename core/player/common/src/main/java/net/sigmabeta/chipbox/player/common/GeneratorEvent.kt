package net.sigmabeta.chipbox.player.common

sealed class GeneratorEvent {
    object Error : GeneratorEvent()

    object Complete : GeneratorEvent()

    data class Audio(
        val bufferNumber: Int,
        val timestampMillis: Double,
        val timestampFrame: Long,
        val sampleRate: Int,
        val data: ShortArray
    ) : GeneratorEvent()
}
