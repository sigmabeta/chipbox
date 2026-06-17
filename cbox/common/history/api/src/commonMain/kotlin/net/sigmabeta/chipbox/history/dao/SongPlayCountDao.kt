package net.sigmabeta.chipbox.history.dao

import androidx.room.Dao
import androidx.room.Query
import net.sigmabeta.chipbox.entities.SongPlayCountEntity

@Dao
interface SongPlayCountDao {
    // SQLite UPSERT: first qualifying play inserts a row with count 1; every later play bumps the
    // count and refreshes the timestamp. One round-trip, no read-modify-write race.
    @Query(
        "INSERT INTO song_play_count (trackId, playCount, lastPlayedMs) VALUES (:trackId, 1, :timeMs) " +
            "ON CONFLICT(trackId) DO UPDATE SET playCount = playCount + 1, lastPlayedMs = :timeMs",
    )
    suspend fun increment(trackId: Long, timeMs: Long)

    @Query("SELECT * FROM song_play_count WHERE trackId = :trackId")
    suspend fun getCountSync(trackId: Long): SongPlayCountEntity?

    @Query("DELETE FROM song_play_count")
    suspend fun nukeTable()
}
