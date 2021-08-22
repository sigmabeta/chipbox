package net.sigmabeta.chipbox.player.generator.fake.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.speaker.Speaker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FakeGeneratorModule {
    @Provides
    @Singleton
    internal fun provideFakeGenerator(speaker: Speaker) = FakeGenerator(speaker)

}