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

    // Resolve a known set of games by id (most-played and other id-driven surfaces) without scanning
    // the whole table.
    @Query("SELECT * FROM game WHERE id IN (:ids)")
    fun getGamesByIds(ids: List<Long>): Flow<List<GameEntity>>

    // Count + offset pick: a stable, surgical alternative to loading every game to choose one
    // (e.g. the date-seeded "game of the day").
    @Query("SELECT COUNT(*) FROM game")
    suspend fun count(): Int

    @Query("SELECT * FROM game ORDER BY id LIMIT 1 OFFSET :offset")
    suspend fun getAtOffset(offset: Int): GameEntity?

    // Home "recently added" row: a random sample of games added at/after :threshold, capped by
    // :limit. The date_added index serves the range filter, so this stays surgical instead of
    // scanning the whole table. RANDOM() shuffles only the (small) in-window set.
    @Query("SELECT * FROM game WHERE date_added >= :threshold ORDER BY RANDOM() LIMIT :limit")
    fun getRecentlyAdded(threshold: Long, limit: Int): Flow<List<GameEntity>>

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
