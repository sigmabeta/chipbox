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
        val bufferSizeBytes = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT) * 10

        val bufferSizeSamples = bufferSizeBytes / 4
        val minimumLatency = 1000 * bufferSizeSamples / sampleRate

        Timber.d("Sample Rate: %d Hz.  Buffer size: %d samples.", sampleRate, bufferSizeSamples)
        Timber.d("Minimum audio latency: %d ms.", minimumLatency)

        return AudioConfig(sampleRate, bufferSizeBytes, minimumLatency)
    }
}