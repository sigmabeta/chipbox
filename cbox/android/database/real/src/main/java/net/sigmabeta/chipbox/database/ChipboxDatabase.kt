package net.sigmabeta.chipbox.database

import androidx.room.Database
import androidx.room.RoomDatabase
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

@Database(
    entities = [
        ArtistEntity::class,
        GameEntity::class,
        TrackEntity::class,
        GameArtistJoin::class,
        TrackArtistJoin::class,
        SearchHistoryEntity::class
    ],
    version = 7
)
@Suppress("TooManyFunctions")
abstract class ChipboxDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun gameDao(): GameDao
    abstract fun trackDao(): TrackDao

    abstract fun gameArtistDao(): GameArtistDao
    abstract fun trackArtistDao(): TrackArtistDao

    abstract fun searchHistoryDao(): SearchHistoryDao
}
