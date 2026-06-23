package net.sigmabeta.chipbox.favorites.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.TrackFavoriteEntity

@Dao
interface TrackFavoriteDao {
    // REPLACE so re-favoriting an already-favorite track simply refreshes its timestamp.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: TrackFavoriteEntity)

    @Query("DELETE FROM track_favorite WHERE trackId = :trackId")
    suspend fun remove(trackId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM track_favorite WHERE trackId = :trackId)")
    fun isFavorite(trackId: Long): Flow<Boolean>

    // Favorited track ids, newest favorite first.
    @Query("SELECT trackId FROM track_favorite ORDER BY favoritedAtMs DESC")
    fun getAllIds(): Flow<List<Long>>

    @Query("DELETE FROM track_favorite")
    suspend fun nukeTable()
}
