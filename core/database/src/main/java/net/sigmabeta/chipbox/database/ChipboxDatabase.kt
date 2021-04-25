package net.sigmabeta.chipbox.database

import androidx.room.Database
import androidx.room.RoomDatabase
import net.sigmabeta.chipbox.database.dao.ArtistDao
import net.sigmabeta.chipbox.entities.ArtistEntity

@Database(
        entities = [ArtistEntity::class],
        version = 1
)
@Suppress("TooManyFunctions")
abstract class ChipboxDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
}
