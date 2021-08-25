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
    @Named(Dependencies.DEP_SAMPLE_RATE)
    internal fun provideSampleRate() =
        AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)

    @Provides
    @Named(Dependencies.DEP_BUFFER_SIZE)
    internal fun provideBufferSize(@Named(Dependencies.DEP_SAMPLE_RATE) sampleRate: Int) =
        AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

    @Provides
    internal fun provideRealSpeaker(
        @Named(Dependencies.DEP_SAMPLE_RATE) sampleRate: Int,
        @Named(Dependencies.DEP_BUFFER_SIZE) bufferSizeBytes: Int,
        generator: Generator
    ) = RealSpeaker(sampleRate, bufferSizeBytes, generator)
}