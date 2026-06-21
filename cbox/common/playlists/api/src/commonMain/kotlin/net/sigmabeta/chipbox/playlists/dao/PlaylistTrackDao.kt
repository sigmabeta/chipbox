package net.sigmabeta.chipbox.playlists.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.PlaylistTrackEntity

@Dao
interface PlaylistTrackDao {
    // IGNORE so re-adding a track already in the playlist is a no-op that keeps its existing position
    // (the `(playlistId, trackId)` composite primary key makes membership unique).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<PlaylistTrackEntity>)

    // Member track ids in user order.
    @Query("SELECT trackId FROM playlist_track WHERE playlistId = :playlistId ORDER BY position")
    fun trackIds(playlistId: Long): Flow<List<Long>>

    // Highest occupied position, or -1 when the playlist is empty (so appends start at 0).
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_track WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("DELETE FROM playlist_track WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)

    @Query("DELETE FROM playlist_track")
    suspend fun nukeTable()
}
