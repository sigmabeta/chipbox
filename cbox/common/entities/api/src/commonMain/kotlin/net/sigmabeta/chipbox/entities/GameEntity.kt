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
    // Hash of the source folder's files (path + size + last-modified). The scanner compares this
    // against a freshly computed hash on rescan to skip re-reading folders that haven't changed.
    @ColumnInfo(name = "folder_signature") val folderSignature: String,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)
