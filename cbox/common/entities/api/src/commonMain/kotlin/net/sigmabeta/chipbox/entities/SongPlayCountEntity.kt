package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Running play-count for a single track, incremented once per qualifying play. [trackId] is the
 * primary key (one row per track) and references the library's `track.id`. Maintained via a SQLite
 * UPSERT in [net.sigmabeta.chipbox.history.dao.SongPlayCountDao].
 */
@Entity(tableName = "song_play_count")
data class SongPlayCountEntity(
    @PrimaryKey val trackId: Long,
    val playCount: Int,
    val lastPlayedMs: Long,
)
