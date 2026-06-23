package net.sigmabeta.chipbox.playlists

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import net.sigmabeta.chipbox.entities.PlaylistEntity
import net.sigmabeta.chipbox.entities.PlaylistTrackEntity
import net.sigmabeta.chipbox.playlists.dao.PlaylistDao
import net.sigmabeta.chipbox.playlists.dao.PlaylistTrackDao

/**
 * Separate Room KMP database for user playlists — kept apart from the library `ChipboxDatabase`
 * because the library is a derived cache that gets destructively rebuilt on rescans, while playlists
 * are user data that must survive that. The `playlist` table holds metadata; `playlist_track` holds
 * memberships (with a FK to `playlist` for cascade-delete) referencing library `track.id`s by value,
 * with no cross-database foreign key.
 *
 * `@ConstructedBy(PlaylistsDatabaseConstructor::class)` lets Room generate a per-target `actual` for
 * the `expect object` below, so the same definition serves Android (framework SQLite) and JVM
 * (bundled SQLite driver).
 */
@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
    ],
    version = 1,
)
@ConstructedBy(PlaylistsDatabaseConstructor::class)
abstract class PlaylistsDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
}

/**
 * Room generates the `actual` for each target at build time (KSP). Suppress the unmatched-expect
 * warning — the per-target actuals exist post-KSP.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object PlaylistsDatabaseConstructor : RoomDatabaseConstructor<PlaylistsDatabase> {
    override fun initialize(): PlaylistsDatabase
}
