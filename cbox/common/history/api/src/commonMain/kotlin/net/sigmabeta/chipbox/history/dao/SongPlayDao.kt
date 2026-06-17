package net.sigmabeta.chipbox.history.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import net.sigmabeta.chipbox.entities.SongPlayEntity

@Dao
interface SongPlayDao {
    @Insert
    suspend fun insert(entry: SongPlayEntity): Long

    @Query("SELECT COUNT(*) FROM song_play")
    suspend fun count(): Int

    @Query("DELETE FROM song_play")
    suspend fun nukeTable()
}
