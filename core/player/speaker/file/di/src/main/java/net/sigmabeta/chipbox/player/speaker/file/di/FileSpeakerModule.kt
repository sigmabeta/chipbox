package net.sigmabeta.chipbox.player.speaker.file.di

import android.content.Context
import android.os.Environment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FileSpeakerModule {
    @Provides
    internal fun provideFileLocation(@ApplicationContext context: Context) =
        Environment.getExternalStorageDirectory()

    @Provides
    @Singleton
    internal fun provideFileSpeaker(
        externalStorageDir: File,
        bufferManager: ConsumerBufferManager
    ) = FileSpeaker(
        externalStorageDir,
        bufferManager
    )

}