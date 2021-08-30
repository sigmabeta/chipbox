package net.sigmabeta.chipbox.player.speaker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import net.sigmabeta.chipbox.player.speaker.real.RealSpeaker
import net.sigmabeta.chipbox.player.speaker.text.TextSpeaker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeakerModule {
    @Provides
    @Singleton
    internal fun provideSpeaker(
        fileSpeaker: FileSpeaker,
        realSpeaker: RealSpeaker,
        textSpeaker: TextSpeaker
    ): Speaker = realSpeaker

}