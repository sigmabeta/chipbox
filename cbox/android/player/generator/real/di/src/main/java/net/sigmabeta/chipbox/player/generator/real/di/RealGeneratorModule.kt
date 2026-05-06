package net.sigmabeta.chipbox.player.generator.real.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.emulators.EmulatorProvider
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealGeneratorModule {
    @Provides
    @Singleton
    fun provideRealGenerator(
        emulatorProvider: EmulatorProvider,
        bufferManager: ProducerBufferManager,
        repository: Repository,
        contentSourceRegistry: ContentSourceRegistry,
        @ApplicationContext context: Context,
        hatchet: Hatchet,
    ) = RealGenerator(repository, contentSourceRegistry, bufferManager, emulatorProvider.emulators, context, hatchet)
}
