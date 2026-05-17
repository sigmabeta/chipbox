package net.sigmabeta.chipbox.entities

import androidx.room.ColumnInfo
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
    val source: String,
    val trackLengthMs: Long,
    val trackNumber: Int,
    val fadeLengthMs: Long,
    @ColumnInfo(name = "game_id") val gameId: Long,
    val chainFiles: String = "",
    val extension: String = "",
    // Stored as Platform.name; the model enum lives in a module this one doesn't depend on.
    val platform: String = "OTHER",
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)
