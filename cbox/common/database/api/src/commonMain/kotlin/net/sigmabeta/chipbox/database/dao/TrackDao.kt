package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.TrackEntity

@Dao
interface TrackDao {
    @Query("SELECT * FROM track ORDER BY title")
    fun getAll(): Flow<List<TrackEntity>>

    // Paged variant of [getAll]. SQLite treats a negative LIMIT as "no limit", so passing -1 yields
    // every row from [offset] onward — letting the repository express "skip N, take the rest".
    @Query("SELECT * FROM track ORDER BY title LIMIT :limit OFFSET :offset")
    fun getAllPaged(limit: Int, offset: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE game_id = :gameId")
    fun getTracksForGame(gameId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE game_id = :gameId")
    suspend fun getTracksForGameSync(gameId: Long): List<TrackEntity>

    @Query("SELECT * FROM track WHERE platform = :platformName")
    suspend fun getTracksForPlatformSync(platformName: String): List<TrackEntity>

    @Query("SELECT DISTINCT platform FROM track")
    fun getDistinctPlatforms(): Flow<List<String>>

    @Query("SELECT * FROM track WHERE id = :trackId")
    fun getTrack(trackId: Long): Flow<TrackEntity>

    @Query("SELECT * FROM track WHERE id IN (:ids)")
    fun getTracksByIds(ids: List<Long>): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE id = :trackId")
    suspend fun getTrackSync(trackId: Long): TrackEntity?

    @Query("SELECT * FROM track WHERE title LIKE :title ORDER BY title COLLATE NOCASE")
    fun searchTracksByTitle(title: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandom(): TrackEntity?

    @Insert
    suspend fun insert(track: TrackEntity): Long

    @Insert
    suspend fun insertAll(tracks: List<TrackEntity>): List<Long>

    @Update
    suspend fun updateAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM track WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM track")
    suspend fun nukeTable()
}
