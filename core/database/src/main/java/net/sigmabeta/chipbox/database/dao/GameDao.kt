package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.GameEntity

@Dao
interface GameDao {
    @Query("SELECT * FROM game WHERE id = :gameId")
    fun getGame(gameId: Long): Flow<GameEntity>

    @Query("SELECT * FROM game ORDER BY title COLLATE NOCASE")
    fun getAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE title LIKE :title ORDER BY title COLLATE NOCASE")
    fun searchGamesByTitle(title: String): Flow<List<GameEntity>>

    @Insert
    fun insertAll(gameEntities: List<GameEntity>)

    @Query("DELETE FROM game")
    fun nukeTable()
}