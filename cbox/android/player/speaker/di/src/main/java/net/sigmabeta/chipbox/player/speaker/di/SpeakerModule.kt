package net.sigmabeta.chipbox.player.speaker.di

import android.content.Context
import android.os.Environment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import net.sigmabeta.chipbox.player.speaker.real.RealSpeaker
import net.sigmabeta.chipbox.player.speaker.text.TextSpeaker
import net.sigmabeta.sage.logging.Hatchet
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeakerModule {
    @Provides
    fun provideFileLocation(@ApplicationContext context: Context): File =
        Environment.getExternalStorageDirectory()

    @Provides
    @Singleton
    fun provideFileSpeaker(
        externalStorageDir: File,
        hatchet: Hatchet,
        bufferManager: ConsumerBufferManager
    ) = FileSpeaker(externalStorageDir, hatchet, bufferManager)

    @Provides
    @Singleton
    fun provideTextSpeaker(
        hatchet: Hatchet,
        bufferManager: ConsumerBufferManager,
    ) = TextSpeaker(hatchet, bufferManager)

    @Provides
    @Singleton
    fun provideRealSpeaker(
        bufferManager: ConsumerBufferManager,
        hatchet: Hatchet,
    ) = RealSpeaker(bufferManager, hatchet)

    @Provides
    @Singleton
    fun provideSpeaker(
        fileSpeaker: FileSpeaker,
        realSpeaker: RealSpeaker,
        textSpeaker: TextSpeaker
    ): Speaker = realSpeaker
}
