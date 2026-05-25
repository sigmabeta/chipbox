package net.sigmabeta.chipbox.repository.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object DatabaseRepositoryModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideRepository(database: ChipboxDatabase, hatchet: Hatchet): Repository =
        provideDatabaseRepository(database, hatchet)

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
