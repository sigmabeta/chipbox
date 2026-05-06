package net.sigmabeta.chipbox.player.generator

/**
 * Things the [Generator]'s loop tells the [net.sigmabeta.chipbox.player.director.Director] about
 * as it produces audio. Consumed via [Generator.events].
 */
sealed class GeneratorEvent {
    /** The generator hit a fatal error and its loop has terminated. */
    data class Error(
        val message: String
    ) : GeneratorEvent()

    /** A new track has been requested and the generator is loading it. Emitted before the
     *  emulator actually starts producing samples for [trackId]. */
    data class Loading(
        val trackId: Long
    ) : GeneratorEvent()

    /** A buffer has just been pushed downstream. Used by the director to flip
     *  [net.sigmabeta.chipbox.player.director.PlayerState.BUFFERING] → `PLAYING`. */
    object Emitting : GeneratorEvent()

    /** The current track has finished and the generator is blocked waiting for the next
     *  track id. The director is expected to respond with [Generator.startTrack]. */
    object TrackChange : GeneratorEvent()
}
