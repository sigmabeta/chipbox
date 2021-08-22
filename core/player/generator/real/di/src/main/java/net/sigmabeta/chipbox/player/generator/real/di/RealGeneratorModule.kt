package net.sigmabeta.chipbox.player.generator.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.player.speaker.Speaker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealGeneratorModule {
    @Provides
    @Singleton
    internal fun provideRealGenerator(speaker: Speaker) = RealGenerator(speaker)
}