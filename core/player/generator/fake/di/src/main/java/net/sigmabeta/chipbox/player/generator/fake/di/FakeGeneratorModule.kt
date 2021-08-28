package net.sigmabeta.chipbox.player.generator.fake.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.common.Dependencies
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.generator.fake.TrackRandomizer
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FakeGeneratorModule {
    @Provides
    @Singleton
    internal fun provideFakeGenerator(
        @Named(Dependencies.DEP_SAMPLE_RATE) sampleRate: Int,
        @Named(Dependencies.DEP_BUFFER_SIZE) bufferSizeBytes: Int,
        trackRandomizer: TrackRandomizer
    ) = FakeGenerator(sampleRate, bufferSizeBytes, trackRandomizer)

    @Provides
    @Singleton
    internal fun provideTrackRandomizer(repository: Repository) = TrackRandomizer(repository)
}