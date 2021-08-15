package net.sigmabeta.chipbox.database

import androidx.room.Database
import androidx.room.RoomDatabase
import net.sigmabeta.chipbox.database.dao.*
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.entities.joins.GameArtistJoin
import net.sigmabeta.chipbox.entities.joins.TrackArtistJoin

@Database(
    entities = [
        ArtistEntity::class,
        GameEntity::class,
        TrackEntity::class,
        GameArtistJoin::class,
        TrackArtistJoin::class
    ],
    version = 1
)
@Suppress("TooManyFunctions")
abstract class ChipboxDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun gameDao(): GameDao
    abstract fun trackDao(): TrackDao

    abstract fun gameArtistDao(): GameArtistDao
    abstract fun trackArtistDao(): TrackArtistDao
}
