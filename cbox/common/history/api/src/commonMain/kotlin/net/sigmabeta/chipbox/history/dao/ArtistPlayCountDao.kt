package net.sigmabeta.chipbox.history.dao

import androidx.room.Dao
import androidx.room.Query
import net.sigmabeta.chipbox.entities.ArtistPlayCountEntity

@Dao
interface ArtistPlayCountDao {
    @Query(
        "INSERT INTO artist_play_count (artistId, playCount, lastPlayedMs) VALUES (:artistId, 1, :timeMs) " +
            "ON CONFLICT(artistId) DO UPDATE SET playCount = playCount + 1, lastPlayedMs = :timeMs",
    )
    suspend fun increment(artistId: Long, timeMs: Long)

    @Query("SELECT * FROM artist_play_count WHERE artistId = :artistId")
    suspend fun getCountSync(artistId: Long): ArtistPlayCountEntity?

    @Query("DELETE FROM artist_play_count")
    suspend fun nukeTable()
}
