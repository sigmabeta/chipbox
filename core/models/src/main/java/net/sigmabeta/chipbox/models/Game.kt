package net.sigmabeta.chipbox.models

data class Game (
    val id: Long,
    val title: String,
    val artists: List<Artist>?,
    val photoUrl: String?,
    val tracks: List<Track>?
)