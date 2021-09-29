package net.sigmabeta.chipbox.player.speaker.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.real.RealSpeaker

@Module
@InstallIn(SingletonComponent::class)
object RealSpeakerModule {
    @Provides
    internal fun provideRealSpeaker(
            bufferManager: ConsumerBufferManager
    ) = RealSpeaker(bufferManager)
}