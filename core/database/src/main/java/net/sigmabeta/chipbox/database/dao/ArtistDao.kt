package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import net.sigmabeta.chipbox.entities.ArtistEntity

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artist WHERE id = :artistId")
    fun getArtist(artistId: Long): ArtistEntity

    @Insert
    fun insertAll(artists: List<ArtistEntity>)
}