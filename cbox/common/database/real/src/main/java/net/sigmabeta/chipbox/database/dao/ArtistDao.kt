package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.ArtistEntity

// Room KMP requires every non-Flow DAO method to be `suspend` on non-Android
// targets — Flow returns stay as plain `fun`.
@Dao
interface ArtistDao {
    @Query("SELECT * FROM artist WHERE id = :artistId")
    fun getArtist(artistId: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM artist WHERE name = :name")
    suspend fun getArtistByNameSync(name: String): ArtistEntity?

    @Query("SELECT * FROM artist ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artist WHERE name LIKE :name ORDER BY name COLLATE NOCASE")
    fun searchArtistsByName(name: String): Flow<List<ArtistEntity>>

    @Insert
    suspend fun insert(artist: ArtistEntity): Long

    @Insert
    suspend fun insertAll(artists: List<ArtistEntity>): List<Long>

    // Reconciliation cleanup: drop artists no track points at any more.
    @Query("DELETE FROM artist WHERE id NOT IN (SELECT DISTINCT artistId FROM track_artist_join)")
    suspend fun deleteOrphans()

    @Query("DELETE FROM artist")
    suspend fun nukeTable()
}
