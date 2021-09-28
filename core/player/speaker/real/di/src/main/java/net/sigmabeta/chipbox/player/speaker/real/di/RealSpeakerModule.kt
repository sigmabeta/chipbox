package net.sigmabeta.chipbox.player.speaker.real.di

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.common.Dependencies
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.real.RealSpeaker
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object RealSpeakerModule {
    @Provides
    internal fun provideRealSpeaker(
        generator: Generator
    ) = RealSpeaker(generator)
}