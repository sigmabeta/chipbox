package net.sigmabeta.chipbox.player.speaker.file.di

import android.content.Context
import android.os.Environment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.common.Dependencies
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FileSpeakerModule {
    @Provides
    fun provideFileLocation(@ApplicationContext context: Context) =
        Environment.getExternalStorageDirectory()


    @Provides
    @Singleton
    internal fun provideFileSpeaker(
        @Named(Dependencies.DEP_SAMPLE_RATE) sampleRate: Int,
        @Named(Dependencies.DEP_BUFFER_SIZE) bufferSizeBytes: Int,
        externalStorageDir: File,
        generator: Generator
    ) = FileSpeaker(
        sampleRate,
        bufferSizeBytes,
        externalStorageDir,
        generator
    )

}