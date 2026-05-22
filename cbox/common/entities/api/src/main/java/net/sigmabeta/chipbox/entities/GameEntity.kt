package net.sigmabeta.chipbox.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "game",
    // Unique: one game per source folder. Lets the scanner upsert a folder's game across rescans
    // and guarantees no duplicate games.
    indices = [Index(value = ["folder_key"], unique = true)]
)
data class GameEntity(
    val title: String,
    val photoUrl: String?,
    @ColumnInfo(name = "folder_key") val folderKey: String,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)
