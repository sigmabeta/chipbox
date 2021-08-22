package net.sigmabeta.chipbox.player.speaker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.player.speaker.real.RealSpeaker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeakerModule {
    @Provides
    @Singleton
    internal fun provideSpeaker(realSpeaker: RealSpeaker): Speaker = realSpeaker

}