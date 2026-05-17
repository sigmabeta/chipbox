package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artist")
data class ArtistEntity(
    val name: String,
    val photoUrl: String? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)
