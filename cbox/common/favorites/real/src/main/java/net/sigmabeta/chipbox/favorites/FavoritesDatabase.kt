package net.sigmabeta.chipbox.favorites

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import net.sigmabeta.chipbox.entities.ArtistFavoriteEntity
import net.sigmabeta.chipbox.entities.GameFavoriteEntity
import net.sigmabeta.chipbox.entities.TrackFavoriteEntity
import net.sigmabeta.chipbox.favorites.dao.ArtistFavoriteDao
import net.sigmabeta.chipbox.favorites.dao.GameFavoriteDao
import net.sigmabeta.chipbox.favorites.dao.TrackFavoriteDao

/**
 * Separate Room KMP database for user favorites — kept apart from the library `ChipboxDatabase`
 * because the library is a derived cache that gets destructively rebuilt on rescans, while favorites
 * must survive that. One table per favoritable type; ids reference library rows by value, with no
 * cross-database foreign keys.
 *
 * `@ConstructedBy(FavoritesDatabaseConstructor::class)` lets Room generate a per-target `actual` for
 * the `expect object` below, so the same definition serves Android (framework SQLite) and JVM
 * (bundled SQLite driver).
 */
@Database(
    entities = [
        TrackFavoriteEntity::class,
        GameFavoriteEntity::class,
        ArtistFavoriteEntity::class,
    ],
    version = 1,
)
@ConstructedBy(FavoritesDatabaseConstructor::class)
abstract class FavoritesDatabase : RoomDatabase() {
    abstract fun trackFavoriteDao(): TrackFavoriteDao
    abstract fun gameFavoriteDao(): GameFavoriteDao
    abstract fun artistFavoriteDao(): ArtistFavoriteDao
}

/**
 * Room generates the `actual` for each target at build time (KSP). Suppress the unmatched-expect
 * warning — the per-target actuals exist post-KSP.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object FavoritesDatabaseConstructor : RoomDatabaseConstructor<FavoritesDatabase> {
    override fun initialize(): FavoritesDatabase
}
