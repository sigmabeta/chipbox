package net.sigmabeta.chipbox.models

import kotlinx.serialization.Serializable

@Serializable
data class Game(
    val id: Long,
    val title: String,
    val photoUrl: String?,
    val artists: List<Artist>?,
    val tracks: List<Track>?,
    // Optional release-level descriptive metadata, null when no scanned track carried it.
    val copyright: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val titleJp: String? = null,
)
