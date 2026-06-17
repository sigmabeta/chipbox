package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per recorded play of a track (logged once a song has been played for 10+ seconds, or
 * played through to its end). Lives in the separate `HistoryDatabase`, so [trackId] is a plain
 * id referencing the library's `track.id` rather than a foreign key — the library is a derived
 * cache that gets destructively rebuilt, which would invalidate any cross-database FK.
 */
@Entity(
    tableName = "song_play",
    indices = [Index(value = ["trackId"]), Index(value = ["timeMs"])],
)
data class SongPlayEntity(
    val trackId: Long,
    val timeMs: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)
