package net.sigmabeta.chipbox.debuginfo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.debuginfo.real.RealDebugInfoManager
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object DebugInfoModule {
    @Provides
    @Singleton
    fun provideDebugInfoManager(
        director: Director,
        generator: Generator,
        speaker: Speaker,
        bufferDebugSource: BufferDebugSource,
        scope: CoroutineScope,
    ): DebugInfoManager = RealDebugInfoManager(
        director,
        generator,
        speaker,
        bufferDebugSource,
        scope,
    )
}
