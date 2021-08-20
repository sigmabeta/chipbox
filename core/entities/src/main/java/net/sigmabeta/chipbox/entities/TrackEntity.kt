package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "track",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("game_id"),
            onDelete = ForeignKey.CASCADE
        )]
)
data class TrackEntity(
    val title: String,
    val path: String,
    val trackLengthMs: Long,
    val trackNumber: Int,
    val fade: Boolean,
    val game_id: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)