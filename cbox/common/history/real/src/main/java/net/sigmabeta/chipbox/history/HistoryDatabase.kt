package net.sigmabeta.chipbox.history

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import net.sigmabeta.chipbox.entities.ArtistPlayCountEntity
import net.sigmabeta.chipbox.entities.GamePlayCountEntity
import net.sigmabeta.chipbox.entities.SongPlayCountEntity
import net.sigmabeta.chipbox.entities.SongPlayEntity
import net.sigmabeta.chipbox.history.dao.ArtistPlayCountDao
import net.sigmabeta.chipbox.history.dao.GamePlayCountDao
import net.sigmabeta.chipbox.history.dao.SongPlayCountDao
import net.sigmabeta.chipbox.history.dao.SongPlayDao

/**
 * Separate Room KMP database for playback history — kept apart from the library `ChipboxDatabase`
 * because the library is a derived cache that gets destructively rebuilt on rescans, while history
 * must survive that. The four tables are individual plays plus per-song/game/artist counters; ids
 * reference library rows by value, with no cross-database foreign keys.
 *
 * `@ConstructedBy(HistoryDatabaseConstructor::class)` lets Room generate a per-target `actual` for
 * the `expect object` below, so the same definition serves Android (framework SQLite) and JVM
 * (bundled SQLite driver).
 */
@Database(
    entities = [
        SongPlayEntity::class,
        SongPlayCountEntity::class,
        GamePlayCountEntity::class,
        ArtistPlayCountEntity::class,
    ],
    version = 1,
)
@ConstructedBy(HistoryDatabaseConstructor::class)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun songPlayDao(): SongPlayDao
    abstract fun songPlayCountDao(): SongPlayCountDao
    abstract fun gamePlayCountDao(): GamePlayCountDao
    abstract fun artistPlayCountDao(): ArtistPlayCountDao
}

/**
 * Room generates the `actual` for each target at build time (KSP). Suppress the unmatched-expect
 * warning — the per-target actuals exist post-KSP.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object HistoryDatabaseConstructor : RoomDatabaseConstructor<HistoryDatabase> {
    override fun initialize(): HistoryDatabase
}
