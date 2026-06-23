package net.sigmabeta.chipbox.favorites.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.GameFavoriteEntity

@Dao
interface GameFavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: GameFavoriteEntity)

    @Query("DELETE FROM game_favorite WHERE gameId = :gameId")
    suspend fun remove(gameId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM game_favorite WHERE gameId = :gameId)")
    fun isFavorite(gameId: Long): Flow<Boolean>

    @Query("SELECT gameId FROM game_favorite ORDER BY favoritedAtMs DESC")
    fun getAllIds(): Flow<List<Long>>

    @Query("DELETE FROM game_favorite")
    suspend fun nukeTable()
}
