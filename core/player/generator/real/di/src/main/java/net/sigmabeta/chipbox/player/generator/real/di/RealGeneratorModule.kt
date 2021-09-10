package net.sigmabeta.chipbox.player.generator.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.common.Dependencies
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealGeneratorModule {
    @Provides
    @Singleton
    internal fun provideRealGenerator(
        @Named(Dependencies.DEP_BUFFER_SIZE) bufferSizeBytes: Int,
        repository: Repository
    ) = RealGenerator(repository)
}