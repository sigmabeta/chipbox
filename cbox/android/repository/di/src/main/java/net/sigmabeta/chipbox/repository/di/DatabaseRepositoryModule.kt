package net.sigmabeta.chipbox.repository.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.RepositorySource
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.repository.memory.MemoryRepository
import net.sigmabeta.chipbox.repository.memory.RandomMemoryRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object DatabaseRepositoryModule {
    // Pick the Repository impl from the debug "repository source" setting, read once at graph build
    // (app launch). The switch takes effect on the next launch — no runtime swapping — so a plain
    // read here is enough. Default REAL = the on-device database.
    @Provides
    @SingleIn(AppScope::class)
    fun provideRepository(
        databaseRepository: DatabaseRepository,
        memoryRepository: MemoryRepository,
        randomMemoryRepository: RandomMemoryRepository,
        debugSettingsManager: DebugSettingsManager,
    ): Repository = when (runBlocking { debugSettingsManager.getRepositorySource().first() }) {
        RepositorySource.REAL -> databaseRepository
        RepositorySource.MEMORY -> memoryRepository
        RepositorySource.RANDOM -> randomMemoryRepository
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseRepository(database: ChipboxDatabase, hatchet: Hatchet): DatabaseRepository =
        DatabaseRepository(
            database.artistDao(),
            database.gameDao(),
            database.trackDao(),
            database.gameArtistDao(),
            database.trackArtistDao(),
            database.searchHistoryDao(),
            hatchet,
        )
}
