package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artist",
    // Unique: artists are matched by name during scanning, so this prevents duplicate artists.
    indices = [Index(value = ["name"], unique = true)]
)
data class ArtistEntity(
    val name: String,
    val photoUrl: String? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)
