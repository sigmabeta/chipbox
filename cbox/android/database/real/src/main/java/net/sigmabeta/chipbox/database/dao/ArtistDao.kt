package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.ArtistEntity

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artist WHERE id = :artistId")
    fun getArtist(artistId: Long): Flow<ArtistEntity>

    @Query("SELECT * FROM artist WHERE name = :name")
    fun getArtistByNameSync(name: String): ArtistEntity?

    @Query("SELECT * FROM artist ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artist WHERE name LIKE :name ORDER BY name COLLATE NOCASE")
    fun searchArtistsByName(name: String): Flow<List<ArtistEntity>>

    @Insert
    fun insert(artist: ArtistEntity): Long

    @Query("DELETE FROM artist")
    fun nukeTable()
}