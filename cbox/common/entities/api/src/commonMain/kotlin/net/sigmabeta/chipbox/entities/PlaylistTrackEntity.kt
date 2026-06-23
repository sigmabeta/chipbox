package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One track's membership in a playlist. Lives in the separate `PlaylistsDatabase`, so [trackId] is a
 * plain id referencing the library's `track.id` — not a foreign key, since the library is a derived
 * cache that gets destructively rebuilt (a cross-database FK would be invalidated).
 *
 * [playlistId] *is* a real foreign key, because [PlaylistEntity] lives in this same database:
 * deleting a playlist cascades its memberships away. The `(playlistId, trackId)` composite primary
 * key makes a track unique within a playlist (re-adding is a no-op via `OnConflictStrategy.IGNORE`),
 * and [position] gives the user-controlled ordering (`ORDER BY position`).
 */
@Entity(
    tableName = "playlist_track",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["playlistId"])],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)
