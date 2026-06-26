package net.sigmabeta.chipbox.history.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.GamePlayCountEntity
import net.sigmabeta.chipbox.history.PlayCount

@Dao
interface GamePlayCountDao {
    @Query(
        "INSERT INTO game_play_count (gameId, playCount, lastPlayedMs) VALUES (:gameId, 1, :timeMs) " +
            "ON CONFLICT(gameId) DO UPDATE SET playCount = playCount + 1, lastPlayedMs = :timeMs",
    )
    suspend fun increment(gameId: Long, timeMs: Long)

    @Query("SELECT * FROM game_play_count WHERE gameId = :gameId")
    suspend fun getCountSync(gameId: Long): GamePlayCountEntity?

    @Query(
        "SELECT gameId AS id, playCount, lastPlayedMs FROM game_play_count " +
            "WHERE playCount > 1 ORDER BY playCount DESC, lastPlayedMs DESC LIMIT :limit",
    )
    fun getMostPlayed(limit: Int): Flow<List<PlayCount>>

    // Every game with at least one play (a row exists here only after the first play increments it),
    // for the Home "New to You" row to subtract from the library.
    @Query("SELECT gameId FROM game_play_count")
    fun getPlayedGameIds(): Flow<List<Long>>

    @Query("DELETE FROM game_play_count")
    suspend fun nukeTable()
}
