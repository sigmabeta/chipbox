package net.sigmabeta.chipbox.player.common

sealed class GeneratorEvent {
    data class Error(
        val message: String
    ) : GeneratorEvent()

    object Loading : GeneratorEvent()

    object Complete : GeneratorEvent()

    data class Audio(
        val bufferNumber: Int,
        val timestampMillis: Double,
        val timestampFrame: Int,
        val sampleRate: Int,
        val data: ShortArray
    ) : GeneratorEvent()
}
