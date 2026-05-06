package net.sigmabeta.chipbox.player.speaker.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.real.RealSpeaker
import net.sigmabeta.sage.logging.Hatchet

@Module
@InstallIn(SingletonComponent::class)
object RealSpeakerModule {
    @Provides
    fun provideRealSpeaker(
            bufferManager: ConsumerBufferManager,
            hatchet: Hatchet,
    ) = RealSpeaker(bufferManager, hatchet)
}
