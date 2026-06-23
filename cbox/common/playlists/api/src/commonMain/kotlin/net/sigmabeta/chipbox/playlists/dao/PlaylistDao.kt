package net.sigmabeta.chipbox.playlists.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.PlaylistEntity

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(entity: PlaylistEntity): Long

    @Query("UPDATE playlist SET name = :name WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String)

    // The `playlist_track` rows cascade away via the membership FK's ON DELETE CASCADE.
    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun delete(playlistId: Long)

    // Each playlist with its member count, newest playlist first. LEFT JOIN so empty playlists count 0.
    @Query(
        """
        SELECT playlist.*, COUNT(playlist_track.trackId) AS trackCount
        FROM playlist
        LEFT JOIN playlist_track ON playlist_track.playlistId = playlist.id
        GROUP BY playlist.id
        ORDER BY playlist.createdAtMs DESC
        """
    )
    fun getAllWithCounts(): Flow<List<PlaylistWithCount>>

    @Query(
        """
        SELECT playlist.*, COUNT(playlist_track.trackId) AS trackCount
        FROM playlist
        LEFT JOIN playlist_track ON playlist_track.playlistId = playlist.id
        WHERE playlist.id = :playlistId
        GROUP BY playlist.id
        """
    )
    fun getByIdWithCount(playlistId: Long): Flow<PlaylistWithCount?>

    @Query("DELETE FROM playlist")
    suspend fun nukeTable()
}
