package net.sigmabeta.chipbox.player.status

/**
 * Coarse player status surfaced by [StatusProvider]. Less detailed than
 * [net.sigmabeta.chipbox.player.director.ChipboxPlaybackState] — meant for consumers that only
 * need "what's happening, on which track" rather than the full reducer state.
 */
sealed class PlayerStatus {
    data class Buffering(
        val trackId: Long
    )

    data class Playing(
        val trackId: Long,
        val trackPosition: Long
    ) : PlayerStatus()

    data class Paused(
        val trackId: Long,
        val trackPosition: Long
    ) : PlayerStatus()

    object Stopped : PlayerStatus()

    data class Error(
        val trackId: Long,
        val message: String,
    ) : PlayerStatus()
}
