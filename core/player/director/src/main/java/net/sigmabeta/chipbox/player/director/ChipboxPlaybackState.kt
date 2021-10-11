package net.sigmabeta.chipbox.player.director

data class ChipboxPlaybackState(
    val state: PlayerState,
    val position: Long,
    val bufferPosition: Long,
    val playbackSpeed: Float,
    val skipForwardAllowed: Boolean,
    val errorMessage: String? = null
)
