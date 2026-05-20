package net.sigmabeta.chipbox.repository.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.repository.memory.MemoryRepository
import javax.inject.Singleton
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideMemoryRepository(): MemoryRepository = MemoryRepository()
}
