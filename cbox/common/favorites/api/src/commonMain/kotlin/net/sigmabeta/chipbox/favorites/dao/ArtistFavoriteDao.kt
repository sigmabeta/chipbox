package net.sigmabeta.chipbox.favorites.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.ArtistFavoriteEntity

@Dao
interface ArtistFavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: ArtistFavoriteEntity)

    @Query("DELETE FROM artist_favorite WHERE artistId = :artistId")
    suspend fun remove(artistId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM artist_favorite WHERE artistId = :artistId)")
    fun isFavorite(artistId: Long): Flow<Boolean>

    @Query("SELECT artistId FROM artist_favorite ORDER BY favoritedAtMs DESC")
    fun getAllIds(): Flow<List<Long>>

    @Query("DELETE FROM artist_favorite")
    suspend fun nukeTable()
}
