package net.sigmabeta.chipbox.repository.memory

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MemoryRepositoryModule {
//    @Provides
//    @Singleton
//    fun provideRepository(): Repository = provideMemoryRepository()

    @Provides
    @Singleton
    fun provideMemoryRepository() = MemoryRepository()
}