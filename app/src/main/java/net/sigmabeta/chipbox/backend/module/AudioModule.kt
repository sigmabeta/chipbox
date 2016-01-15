package net.sigmabeta.chipbox.backend.module

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.model.objects.AudioConfig
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logVerbose
import javax.inject.Singleton

@Module
class AudioModule {
    @Provides @Singleton fun provideAudioConfig(): AudioConfig {
        logVerbose("[AudioModule] Providing AudioConfig...")
        val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        val bufferSizeBytes = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT)

        val bufferSizeSamples = bufferSizeBytes / 4
        val minimumLatency = 1000 * bufferSizeSamples / sampleRate

        logDebug("[AudioModule] Sample Rate: ${sampleRate}Hz.  Buffer size: $bufferSizeSamples samples.")
        logDebug("[AudioModule] Minimum audio latency: ${minimumLatency}ms.")

        return AudioConfig(sampleRate, bufferSizeBytes, minimumLatency)
    }

    @Provides @Singleton fun providePlayer(audioConfig: AudioConfig, audioManager: AudioManager, context: Context): Player {
        logVerbose("[AudioModule] Providing Player...")
        return Player(audioConfig, audioManager, context)
    }
}