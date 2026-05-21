package net.sigmabeta.chipbox.debuginfo.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.debuginfo.real.RealDebugInfoManager
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object DebugInfoModule {
    @Provides
    @SingleIn(AppScope::class)
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
