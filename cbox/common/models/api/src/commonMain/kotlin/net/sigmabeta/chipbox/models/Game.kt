package net.sigmabeta.chipbox.models

import kotlinx.serialization.Serializable

@Serializable
data class Game(
    val id: Long,
    val title: String,
    val photoUrl: String?,
    val artists: List<Artist>?,
    val tracks: List<Track>?
)
