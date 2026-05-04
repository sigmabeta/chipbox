package net.sigmabeta.chipbox.player.generator.fake.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FakeGeneratorModule {
    @Provides
    @Singleton
    fun provideFakeGenerator(
        repository: Repository,
        bufferManager: ProducerBufferManager,
        contentSourceRegistry: ContentSourceRegistry,
        hatchet: Hatchet,
    ) = FakeGenerator(repository, contentSourceRegistry, bufferManager, hatchet)
}
