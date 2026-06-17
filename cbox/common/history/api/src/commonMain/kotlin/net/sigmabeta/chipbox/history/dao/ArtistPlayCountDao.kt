package net.sigmabeta.chipbox.history.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.ArtistPlayCountEntity
import net.sigmabeta.chipbox.history.PlayCount

@Dao
interface ArtistPlayCountDao {
    @Query(
        "INSERT INTO artist_play_count (artistId, playCount, lastPlayedMs) VALUES (:artistId, 1, :timeMs) " +
            "ON CONFLICT(artistId) DO UPDATE SET playCount = playCount + 1, lastPlayedMs = :timeMs",
    )
    suspend fun increment(artistId: Long, timeMs: Long)

    @Query("SELECT * FROM artist_play_count WHERE artistId = :artistId")
    suspend fun getCountSync(artistId: Long): ArtistPlayCountEntity?

    @Query(
        "SELECT artistId AS id, playCount, lastPlayedMs FROM artist_play_count " +
            "WHERE playCount > 1 ORDER BY playCount DESC, lastPlayedMs DESC LIMIT :limit",
    )
    fun getMostPlayed(limit: Int): Flow<List<PlayCount>>

    @Query("DELETE FROM artist_play_count")
    suspend fun nukeTable()
}
