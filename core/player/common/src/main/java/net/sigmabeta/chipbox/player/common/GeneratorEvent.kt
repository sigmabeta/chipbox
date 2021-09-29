package net.sigmabeta.chipbox.player.common

sealed class GeneratorEvent {
    data class Error(
        val message: String
    ) : GeneratorEvent()

    object Loading : GeneratorEvent()

    object Complete : GeneratorEvent()
}
