package net.sigmabeta.chipbox.player.generator

sealed class GeneratorEvent {
    // What?!
    data class Error(
        val message: String
    ) : GeneratorEvent()

    // Preparing to play a new track.
    data class Loading(
        val trackId: Long
    ) : GeneratorEvent()

    // Track loaded, sending data for a Speaker to play.
    object Emitting : GeneratorEvent()

    // Requesting a new track to play.
    object TrackChange : GeneratorEvent()
}
