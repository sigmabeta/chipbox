package net.sigmabeta.chipbox.entities.joins

import androidx.room.Entity
import androidx.room.ForeignKey
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.TrackEntity

@Entity(
    tableName = "track_artist_join",
    primaryKeys = ["trackId", "artistId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("trackId"),
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("artistId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackArtistJoin(val trackId: Long, val artistId: Long)