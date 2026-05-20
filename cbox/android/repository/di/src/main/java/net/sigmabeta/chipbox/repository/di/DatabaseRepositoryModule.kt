package net.sigmabeta.chipbox.repository.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object DatabaseRepositoryModule {
    @Provides
    @Singleton
    fun provideRepository(database: ChipboxDatabase, hatchet: Hatchet): Repository =
        provideDatabaseRepository(database, hatchet)

    @Provides
    @Singleton
    fun provideDatabaseRepository(database: ChipboxDatabase, hatchet: Hatchet): DatabaseRepository =
        DatabaseRepository(database, hatchet)
}
