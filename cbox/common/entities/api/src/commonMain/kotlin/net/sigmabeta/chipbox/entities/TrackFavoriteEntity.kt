package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Marks one track as a user favorite. Lives in the separate `FavoritesDatabase`, so [trackId] is a
 * plain id referencing the library's `track.id` rather than a foreign key — the library is a derived
 * cache that gets destructively rebuilt, which would invalidate any cross-database FK. [favoritedAtMs]
 * orders the favorites list newest-first.
 */
@Entity(
    tableName = "track_favorite",
    indices = [Index(value = ["favoritedAtMs"])],
)
data class TrackFavoriteEntity(
    @PrimaryKey val trackId: Long,
    val favoritedAtMs: Long,
)
