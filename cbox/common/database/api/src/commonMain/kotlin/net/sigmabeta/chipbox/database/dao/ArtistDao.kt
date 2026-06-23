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

    // Single-row by-id lookup behind the artist-by-id read cache: hydration resolves each artist
    // id (from the join queries below) through this, so a shared artist is read from storage once
    // and reused across every track/game that references it.
    @Query("SELECT * FROM artist WHERE id = :artistId")
    suspend fun getArtistByIdSync(artistId: Long): ArtistEntity

    @Query("SELECT * FROM artist ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artist WHERE name LIKE :name ORDER BY name COLLATE NOCASE")
    fun searchArtistsByName(name: String): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artist ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandom(): ArtistEntity?

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
