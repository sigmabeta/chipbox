package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.SearchHistoryEntity

@Dao
interface SearchHistoryDao {
    // Most-recent first, capped — mirrors VGLS's "ORDER BY timeMs DESC LIMIT 10".
    @Query("SELECT * FROM search_history ORDER BY timeMs DESC LIMIT 10")
    fun getRecent(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE query = :query LIMIT 1")
    suspend fun getByQuerySync(query: String): SearchHistoryEntity?

    @Insert
    suspend fun insert(entry: SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
