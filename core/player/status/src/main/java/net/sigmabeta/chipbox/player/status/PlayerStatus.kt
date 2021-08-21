package net.sigmabeta.chipbox.player.status

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
