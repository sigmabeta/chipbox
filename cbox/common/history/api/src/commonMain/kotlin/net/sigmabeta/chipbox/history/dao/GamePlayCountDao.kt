package net.sigmabeta.chipbox.history.dao

import androidx.room.Dao
import androidx.room.Query
import net.sigmabeta.chipbox.entities.GamePlayCountEntity

@Dao
interface GamePlayCountDao {
    @Query(
        "INSERT INTO game_play_count (gameId, playCount, lastPlayedMs) VALUES (:gameId, 1, :timeMs) " +
            "ON CONFLICT(gameId) DO UPDATE SET playCount = playCount + 1, lastPlayedMs = :timeMs",
    )
    suspend fun increment(gameId: Long, timeMs: Long)

    @Query("SELECT * FROM game_play_count WHERE gameId = :gameId")
    suspend fun getCountSync(gameId: Long): GamePlayCountEntity?

    @Query("DELETE FROM game_play_count")
    suspend fun nukeTable()
}
