package net.sigmabeta.chipbox.player.generator.di

import android.content.Context
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.GeneratorSource
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.emulators.EmulatorProvider
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.fake.SynthGenerator
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path.Companion.toPath

@BindingContainer
@ContributesTo(AppScope::class)
object GeneratorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideRealGenerator(
        emulatorProvider: EmulatorProvider,
        bufferManager: ProducerBufferManager,
        repository: Repository,
        contentSourceRegistry: ContentSourceRegistry,
        context: Context,
        hatchet: Hatchet,
    ): RealGenerator = RealGenerator(
        repository,
        contentSourceRegistry,
        bufferManager,
        emulatorProvider.emulators,
        File(context.cacheDir, "playback").absolutePath.toPath(),
        File(context.cacheDir, "pcm-cache").absolutePath.toPath(),
        FileSystem.SYSTEM,
        hatchet,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideSynthGenerator(
        repository: Repository,
        bufferManager: ProducerBufferManager,
        contentSourceRegistry: ContentSourceRegistry,
        hatchet: Hatchet,
    ): SynthGenerator = SynthGenerator(repository, contentSourceRegistry, bufferManager, hatchet)

    // Pick the Generator impl from the debug "generator source" setting, read once at graph build
    // (app launch); the switch takes effect on the next launch. FAKE = the in-process synth.
    @Provides
    @SingleIn(AppScope::class)
    fun provideGenerator(
        synthGenerator: SynthGenerator,
        realGenerator: RealGenerator,
        debugSettingsManager: DebugSettingsManager,
    ): Generator = when (runBlocking { debugSettingsManager.getGeneratorSource().first() }) {
        GeneratorSource.REAL -> realGenerator
        GeneratorSource.FAKE -> synthGenerator
    }
}
