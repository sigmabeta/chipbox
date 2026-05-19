package net.sigmabeta.chipbox.player.generator.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.emulators.EmulatorProvider
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeneratorModule {
    @Provides
    @Singleton
    fun provideRealGenerator(
        emulatorProvider: EmulatorProvider,
        bufferManager: ProducerBufferManager,
        repository: Repository,
        contentSourceRegistry: ContentSourceRegistry,
        @ApplicationContext context: Context,
        hatchet: Hatchet,
    ) = RealGenerator(
        repository,
        contentSourceRegistry,
        bufferManager,
        emulatorProvider.emulators,
        File(context.cacheDir, "playback"),
        File(context.cacheDir, "pcm-cache"),
        hatchet,
    )

    @Provides
    @Singleton
    fun provideFakeGenerator(
        repository: Repository,
        bufferManager: ProducerBufferManager,
        contentSourceRegistry: ContentSourceRegistry,
        hatchet: Hatchet,
    ) = FakeGenerator(repository, contentSourceRegistry, bufferManager, hatchet)

    @Provides
    @Singleton
    fun provideGenerator(
        fakeGenerator: FakeGenerator,
        realGenerator: RealGenerator
    ): Generator = realGenerator
}
