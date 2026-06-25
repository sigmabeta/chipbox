package net.sigmabeta.chipbox.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("game_id"),
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        // A track's identity is (path, trackNumber): multi-track files (NSF/GBS/...) emit several
        // tracks sharing one path, distinguished by trackNumber. Unique so rescans reconcile by
        // this key and never duplicate.
        Index(value = ["path", "trackNumber"], unique = true),
        // FK target; also speeds the per-game reconciliation lookups.
        Index(value = ["game_id"]),
    ]
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
    // Optional descriptive metadata, null when the source file carried none.
    val comment: String? = null,
    val dumper: String? = null,
    @ColumnInfo(name = "dump_date") val dumpDate: String? = null,
    @ColumnInfo(name = "title_jp") val titleJp: String? = null,
    @ColumnInfo(name = "artist_jp") val artistJp: String? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)
