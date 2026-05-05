package net.sigmabeta.chipbox.entities.joins

import androidx.room.Entity
import androidx.room.ForeignKey
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity

@Entity(
    tableName = "game_artist_join",
    primaryKeys = ["gameId", "artistId"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("gameId"),
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
data class GameArtistJoin(val gameId: Long, val artistId: Long)