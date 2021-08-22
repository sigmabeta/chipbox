package net.sigmabeta.chipbox.player.speaker.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.speaker.real.RealSpeaker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealSpeakerModule {
    @Provides
    @Singleton
    internal fun provideRealSpeaker() = RealSpeaker()

}