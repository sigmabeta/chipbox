package net.sigmabeta.chipbox.player.speaker.di

import android.content.Context
import android.media.AudioManager
import android.os.Environment
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import net.sigmabeta.chipbox.player.speaker.real.RealSpeaker
import net.sigmabeta.chipbox.player.speaker.text.TextSpeaker
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path.Companion.toPath

@BindingContainer
@ContributesTo(AppScope::class)
object SpeakerModule {
    @Provides
    fun provideFileLocation(context: Context): File = Environment.getExternalStorageDirectory()

    @Provides
    @SingleIn(AppScope::class)
    fun provideFileSpeaker(
        externalStorageDir: File,
        hatchet: Hatchet,
        bufferManager: ConsumerBufferManager
    ): FileSpeaker = FileSpeaker(externalStorageDir.absolutePath.toPath(), FileSystem.SYSTEM, hatchet, bufferManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTextSpeaker(
        hatchet: Hatchet,
        bufferManager: ConsumerBufferManager,
    ): TextSpeaker = TextSpeaker(hatchet, bufferManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRealSpeaker(
        context: Context,
        bufferManager: ConsumerBufferManager,
        hatchet: Hatchet,
        settingsManager: ChipboxSettingsManager,
    ): RealSpeaker {
        // The device's preferred output rate: for the in-app resampler modes we open AudioTrack here
        // so the framework mixer never has to resample a non-standard emulator rate (the cause of the
        // odd-rate underruns). The chosen mode (incl. OS passthrough) comes from settings.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?: DEFAULT_OUTPUT_SAMPLE_RATE
        return RealSpeaker(
            bufferManager,
            hatchet,
            settingsManager.getResamplerMode(),
            outputRate,
        )
    }

    /** Fallback when the platform doesn't report a preferred rate; 48 kHz is the modern default. */
    private const val DEFAULT_OUTPUT_SAMPLE_RATE = 48_000

    @Provides
    @SingleIn(AppScope::class)
    fun provideSpeaker(
        fileSpeaker: FileSpeaker,
        realSpeaker: RealSpeaker,
        textSpeaker: TextSpeaker
    ): Speaker = realSpeaker
}
