package net.sigmabeta.chipbox.player.director

/**
 * Snapshot of the player's observable state, emitted by [Director.playbackState] whenever
 * any field changes.
 *
 * @property state High-level lifecycle. See [PlayerState].
 * @property position Current playback position in milliseconds within the active track.
 * @property generatorProducedMs Generator's high-water mark within the active track, in ms.
 *           [generatorProducedMs] − [position] is how much PCM has been produced but not yet
 *           played — the visible play-out buffer.
 * @property playbackSpeed Multiplier applied to generation rate; 1.0 is normal speed.
 * @property skipForwardAllowed False when the current track is the last in the setlist; the UI
 *           uses this to disable the "next" affordance.
 * @property errorMessage Set when [state] is [PlayerState.ERROR], otherwise null.
 */
data class ChipboxPlaybackState(
    val state: PlayerState,
    val position: Long,
    val generatorProducedMs: Long,
    val playbackSpeed: Float,
    val skipForwardAllowed: Boolean,
    val errorMessage: String? = null
)
