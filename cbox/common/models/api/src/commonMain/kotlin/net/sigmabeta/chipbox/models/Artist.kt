package net.sigmabeta.chipbox.models

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
        val id: Long,
        val name: String,
        val photoUrl: String?,
        val tracks: List<Track>?,
        val games: List<Game>?
)
