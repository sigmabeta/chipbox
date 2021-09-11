package net.sigmabeta.chipbox.player.generator.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.EmulatorProvider
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealGeneratorModule {
    @Provides
    @Singleton
    internal fun provideRealGenerator(
        emulatorProvider: EmulatorProvider,
        repository: Repository
    ) = RealGenerator(repository, emulatorProvider.emulators)
}