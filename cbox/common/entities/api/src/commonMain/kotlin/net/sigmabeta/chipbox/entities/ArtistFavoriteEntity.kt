package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Marks one artist as a user favorite. Lives in the separate `FavoritesDatabase`; [artistId]
 * references the library's `artist.id` by value (no cross-database FK — the library is rebuilt
 * destructively). [favoritedAtMs] orders the favorites list newest-first.
 */
@Entity(
    tableName = "artist_favorite",
    indices = [Index(value = ["favoritedAtMs"])],
)
data class ArtistFavoriteEntity(
    @PrimaryKey val artistId: Long,
    val favoritedAtMs: Long,
)
