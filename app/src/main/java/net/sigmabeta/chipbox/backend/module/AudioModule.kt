package net.sigmabeta.chipbox.backend.module

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.model.audio.AudioConfig
import timber.log.Timber
import javax.inject.Singleton

@Module
class AudioModule {
    @Provides @Singleton fun provideAudioConfig(): AudioConfig {
        Timber.v("Providing AudioConfig...")
        val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT)

        return AudioConfig(sampleRate, minBufferSize, 1, 3)
    }
}