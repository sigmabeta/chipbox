package net.sigmabeta.chipbox.player.director.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.real.RealDirector
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object DirectorModule {
    @Provides
    @Singleton
    fun provideDirector(
        generator: Generator,
        speaker: Speaker,
        repository: Repository,
        hatchet: Hatchet,
    ): Director = RealDirector(generator, speaker, repository, hatchet)
}
