package net.sigmabeta.chipbox.history.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.SongPlayEntity
import net.sigmabeta.chipbox.history.RecentPlay

@Dao
interface SongPlayDao {
    @Insert
    suspend fun insert(entry: SongPlayEntity): Long

    @Query("SELECT COUNT(*) FROM song_play")
    suspend fun count(): Int

    // Most-recently-played distinct tracks, newest first. GROUP BY collapses repeat plays of the
    // same track to a single row stamped with its latest play time.
    @Query(
        "SELECT trackId, MAX(timeMs) AS timeMs FROM song_play " +
            "GROUP BY trackId ORDER BY timeMs DESC LIMIT :limit",
    )
    fun getRecentDistinct(limit: Int): Flow<List<RecentPlay>>

    @Query("DELETE FROM song_play")
    suspend fun nukeTable()
}
