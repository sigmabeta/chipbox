package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.TrackEntity

@Dao
interface TrackDao {
    @Query("SELECT * FROM track ORDER BY title")
    fun getAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE game_id = :gameId ORDER BY title")
    fun getTracksForGame(gameId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE game_id = :gameId ORDER BY title")
    fun getTracksForGameSync(gameId: Long): List<TrackEntity>

    @Query("SELECT * FROM track WHERE id = :trackId")
    fun getTrack(trackId: Long): Flow<TrackEntity>

    @Query("SELECT * FROM track WHERE id = :trackId")
    fun getTrackSync(trackId: Long): TrackEntity?

    @Query("SELECT * FROM track WHERE title LIKE :title ORDER BY title")
    fun searchTracksByTitle(title: String): Flow<List<TrackEntity>>

    @Insert
    fun insert(track: TrackEntity): Long

    @Query("DELETE FROM track")
    fun nukeTable()
}
