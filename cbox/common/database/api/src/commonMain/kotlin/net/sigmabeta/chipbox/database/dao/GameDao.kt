package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.GameEntity

@Dao
interface GameDao {
    @Query("SELECT * FROM game WHERE id = :gameId")
    fun getGame(gameId: Long): Flow<GameEntity?>

    // Reconciliation: match a scanned folder to its existing game row.
    @Query("SELECT * FROM game WHERE folder_key = :folderKey")
    suspend fun getByFolderKeySync(folderKey: String): GameEntity?

    @Query("SELECT * FROM game")
    suspend fun getAllSync(): List<GameEntity>

    // Pre-scan snapshot: each game's folder signature + track count, for skip-unchanged decisions.
    @Query(
        "SELECT game.folder_key AS folderKey, game.folder_signature AS signature, " +
            "COUNT(track.id) AS trackCount FROM game " +
            "LEFT JOIN track ON track.game_id = game.id GROUP BY game.id"
    )
    suspend fun getSignatureRows(): List<GameSignatureRow>

    @Update
    suspend fun update(game: GameEntity)

    @Query("DELETE FROM game WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM game WHERE id = :gameId")
    suspend fun getGameSync(gameId: Long): GameEntity

    @Query("SELECT * FROM game ORDER BY title COLLATE NOCASE")
    fun getAll(): Flow<List<GameEntity>>

    // Paged variant of [getAll]. SQLite treats a negative LIMIT as "no limit", so passing -1 yields
    // every row from [offset] onward — letting the repository express "skip N, take the rest".
    @Query("SELECT * FROM game ORDER BY title COLLATE NOCASE LIMIT :limit OFFSET :offset")
    fun getAllPaged(limit: Int, offset: Int): Flow<List<GameEntity>>

    @Query(
        "SELECT DISTINCT game.* FROM game " +
            "INNER JOIN track ON track.game_id = game.id " +
            "WHERE track.platform = :platformName " +
            "ORDER BY game.title COLLATE NOCASE"
    )
    fun getGamesForPlatform(platformName: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE title LIKE :title ORDER BY title COLLATE NOCASE")
    fun searchGamesByTitle(title: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM game ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandom(): GameEntity?

    @Insert
    suspend fun insert(game: GameEntity): Long

    @Query("DELETE FROM game")
    suspend fun nukeTable()
}

/** Projection for [GameDao.getSignatureRows]. */
data class GameSignatureRow(
    val folderKey: String,
    val signature: String,
    val trackCount: Int,
)
