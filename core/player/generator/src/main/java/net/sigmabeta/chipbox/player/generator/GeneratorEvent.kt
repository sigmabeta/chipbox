package net.sigmabeta.chipbox.player.generator

sealed class GeneratorEvent {
    data class Error(
        val message: String
    ) : GeneratorEvent()

    object Buffering : GeneratorEvent()

    object Emitting : GeneratorEvent()

    object Complete : GeneratorEvent()
}
