package net.sigmabeta.chipbox.player.speaker.text.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.speaker.text.TextSpeaker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TextSpeakerModule {
    @Provides
    @Singleton
    internal fun provideTextSpeaker() = TextSpeaker()

}