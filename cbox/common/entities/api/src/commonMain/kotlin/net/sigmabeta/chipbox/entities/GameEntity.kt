package net.sigmabeta.chipbox.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "game",
    // Unique: one game per source folder. Lets the scanner upsert a folder's game across rescans
    // and guarantees no duplicate games. The date_added index serves the "recently added" Home row's
    // range query without a full-table scan.
    indices = [
        Index(value = ["folder_key"], unique = true),
        Index(value = ["date_added"]),
    ]
)
data class GameEntity(
    val title: String,
    val photoUrl: String?,
    @ColumnInfo(name = "folder_key") val folderKey: String,
    // Hash of the source folder's files (path + size + last-modified). The scanner compares this
    // against a freshly computed hash on rescan to skip re-reading folders that haven't changed.
    @ColumnInfo(name = "folder_signature") val folderSignature: String,
    // Optional release-level descriptive metadata, null when no scanned track carried it.
    val copyright: String? = null,
    @ColumnInfo(name = "release_date") val releaseDate: String? = null,
    val genre: String? = null,
    @ColumnInfo(name = "title_jp") val titleJp: String? = null,
    // Wall-clock millis (epoch). date_added is set once when the game is first inserted and never
    // changed; date_last_updated is bumped whenever a rescan meaningfully updates the game.
    @ColumnInfo(name = "date_added") val dateAdded: Long = 0,
    @ColumnInfo(name = "date_last_updated") val dateLastUpdated: Long = 0,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)
