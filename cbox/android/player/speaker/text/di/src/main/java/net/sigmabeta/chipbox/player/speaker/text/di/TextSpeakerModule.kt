package net.sigmabeta.chipbox.player.speaker.text.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.text.TextSpeaker
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TextSpeakerModule {
    @Provides
    @Singleton
    fun provideTextSpeaker(
        hatchet: Hatchet,
            consumerBufferManager: ConsumerBufferManager,
    ) = TextSpeaker(
        hatchet,
            consumerBufferManager
    )
}