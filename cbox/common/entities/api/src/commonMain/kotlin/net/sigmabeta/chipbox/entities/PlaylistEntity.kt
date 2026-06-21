package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-created playlist. Lives in the separate `PlaylistsDatabase` (alongside [PlaylistTrackEntity])
 * so it survives the library's destructive rebuilds — the library `ChipboxDatabase` is a derived
 * cache, but playlists are user data. [createdAtMs] orders the playlists list newest-first; the
 * member tracks (and their order) live in [PlaylistTrackEntity].
 */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    val name: String,
    val createdAtMs: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)
