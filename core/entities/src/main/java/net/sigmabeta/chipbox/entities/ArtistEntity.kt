package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
        tableName = "artist"
)
data class ArtistEntity(
        @PrimaryKey val id: Long,
        val name: String,
        val photoUrl: String? = null
)