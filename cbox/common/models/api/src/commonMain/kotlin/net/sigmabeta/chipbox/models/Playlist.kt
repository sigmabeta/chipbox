package net.sigmabeta.chipbox.models

import kotlinx.serialization.Serializable

/**
 * A user-created playlist's metadata. [trackCount] is denormalized from the membership table for the
 * list screen, which shows each playlist's size without hydrating its tracks. The member [Track]s
 * themselves are hydrated on demand by the detail screen via the library `Repository` — the
 * playlists database stores only ids, since the library is a separately-rebuilt cache.
 */
@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val createdAtMs: Long,
)
