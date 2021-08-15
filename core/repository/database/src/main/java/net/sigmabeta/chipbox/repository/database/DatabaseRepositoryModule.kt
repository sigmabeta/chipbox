package net.sigmabeta.chipbox.repository.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseRepositoryModule {
    @Provides
    @Singleton
    fun provideRepository(): Repository = provideDatabaseRepository()

    @Provides
    @Singleton
    fun provideDatabaseRepository() = DatabaseRepository()
}