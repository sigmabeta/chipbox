package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Marks one game as a user favorite. Lives in the separate `FavoritesDatabase`; [gameId] references
 * the library's `game.id` by value (no cross-database FK — the library is rebuilt destructively).
 * [favoritedAtMs] orders the favorites list newest-first.
 */
@Entity(
    tableName = "game_favorite",
    indices = [Index(value = ["favoritedAtMs"])],
)
data class GameFavoriteEntity(
    @PrimaryKey val gameId: Long,
    val favoritedAtMs: Long,
)
