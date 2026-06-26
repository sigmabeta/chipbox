package net.sigmabeta.chipbox.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import net.sigmabeta.chipbox.database.dao.ArtistDao
import net.sigmabeta.chipbox.database.dao.GameArtistDao
import net.sigmabeta.chipbox.database.dao.GameDao
import net.sigmabeta.chipbox.database.dao.SearchHistoryDao
import net.sigmabeta.chipbox.database.dao.TrackArtistDao
import net.sigmabeta.chipbox.database.dao.TrackDao
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.SearchHistoryEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.entities.joins.GameArtistJoin
import net.sigmabeta.chipbox.entities.joins.TrackArtistJoin

/**
 * Room KMP database. The `@ConstructedBy(ChipboxDatabaseConstructor::class)`
 * lets Room generate a per-target `actual` for the `expect object` below, so
 * the same `@Database` definition serves both Android (framework SQLite) and
 * JVM (bundled SQLite driver) without per-target source duplication.
 */
@Database(
    entities = [
        ArtistEntity::class,
        GameEntity::class,
        TrackEntity::class,
        GameArtistJoin::class,
        TrackArtistJoin::class,
        SearchHistoryEntity::class
    ],
    // v8: games gain a unique folder_key; track (path, trackNumber) and artist name become unique.
    // v9: games gain folder_signature for skip-unchanged-folder rescans.
    // v10: tracks gain comment/dumper/dump_date/title_jp/artist_jp; games gain
    //      copyright/release_date/genre/title_jp — optional descriptive metadata from file tags.
    // v11: tracks and games gain date_added/date_last_updated (epoch millis) recorded by the scanner;
    //      games gain a date_added index for the "recently added" Home row's range query.
    // Upgrades are handled by fallbackToDestructiveMigration (the library is a derived cache and is
    // rebuilt on the next scan), which also clears any duplicate rows left by older insert-only
    // rescans.
    version = 11
)
@ConstructedBy(ChipboxDatabaseConstructor::class)
@Suppress("TooManyFunctions")
abstract class ChipboxDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun gameDao(): GameDao
    abstract fun trackDao(): TrackDao

    abstract fun gameArtistDao(): GameArtistDao
    abstract fun trackArtistDao(): TrackArtistDao

    abstract fun searchHistoryDao(): SearchHistoryDao
}

/**
 * Room generates the `actual` for each target at build time (KSP). Suppress
 * the unmatched-expect warning — the per-target actuals exist post-KSP.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object ChipboxDatabaseConstructor : RoomDatabaseConstructor<ChipboxDatabase> {
    override fun initialize(): ChipboxDatabase
}
