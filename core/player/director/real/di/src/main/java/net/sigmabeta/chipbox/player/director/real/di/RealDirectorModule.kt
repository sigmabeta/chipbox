package net.sigmabeta.chipbox.player.director.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.real.RealDirector
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealDirectorModule {
    @Provides
    @Singleton
    internal fun provideRealDirector(
            generator: Generator,
            speaker: Speaker,
            repository: Repository
    ): Director = RealDirector(generator, speaker, repository)
}