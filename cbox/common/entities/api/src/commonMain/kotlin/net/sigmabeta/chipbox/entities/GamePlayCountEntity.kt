package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Running play-count for a game, incremented once for every qualifying play of any track belonging
 * to it. [gameId] is the primary key and references the library's `game.id`. Maintained via a
 * SQLite UPSERT in [net.sigmabeta.chipbox.history.dao.GamePlayCountDao].
 */
@Entity(tableName = "game_play_count")
data class GamePlayCountEntity(
    @PrimaryKey val gameId: Long,
    val playCount: Int,
    val lastPlayedMs: Long,
)
