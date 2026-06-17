package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Running play-count for an artist, incremented once for every qualifying play of a track the
 * artist is credited on. [artistId] is the primary key and references the library's `artist.id`.
 * Maintained via a SQLite UPSERT in [net.sigmabeta.chipbox.history.dao.ArtistPlayCountDao].
 */
@Entity(tableName = "artist_play_count")
data class ArtistPlayCountEntity(
    @PrimaryKey val artistId: Long,
    val playCount: Int,
    val lastPlayedMs: Long,
)
