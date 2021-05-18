package net.sigmabeta.chipbox.models

data class Game (
    val id: Long,
    val title: String,
    val artist: String?,
    val variousArtists: Boolean,
    val photoUrl: String?,
    val tracks: List<Track>?
)