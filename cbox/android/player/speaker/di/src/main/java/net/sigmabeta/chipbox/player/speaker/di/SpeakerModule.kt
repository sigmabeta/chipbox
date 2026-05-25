package net.sigmabeta.chipbox.player.speaker.di

import android.content.Context
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
        bufferManager: ConsumerBufferManager,
        hatchet: Hatchet,
    ): RealSpeaker = RealSpeaker(bufferManager, hatchet)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSpeaker(
        fileSpeaker: FileSpeaker,
        realSpeaker: RealSpeaker,
        textSpeaker: TextSpeaker
    ): Speaker = realSpeaker
}
