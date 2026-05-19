package net.sigmabeta.chipbox.player.generator.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.cache.real.RealPcmTrackSourceFactory
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet
import java.io.File

/**
 * Platform-agnostic production [Generator]. Identical wiring to the Android
 * `:cbox:android:player:generator:real` twin, except the staging / PCM-cache directories are
 * passed in as plain [File]s instead of being derived from an Android `Context.cacheDir`.
 *
 * That `Context` was the only Android dependency in the original — the real work already lives
 * in the pure-JVM `:cbox:common:player:cache:real` ([RealPcmTrackSourceFactory]) — so hoisting
 * the wiring here lets the headless JVM target (and, later, the Android app) drive real native
 * emulators through one shared code path.
 */
class RealGenerator(
    repository: Repository,
    contentSourceRegistry: ContentSourceRegistry,
    bufferManager: ProducerBufferManager,
    emulators: List<Emulator>,
    stagingDir: File,
    pcmCacheDir: File,
    hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Generator(repository, contentSourceRegistry, bufferManager, hatchet, dispatcher) {

    override val pcmSourceFactory: PcmTrackSource.Factory = RealPcmTrackSourceFactory(
        emulators = emulators,
        stagingDir = stagingDir,
        pcmCacheDir = pcmCacheDir,
        contentSourceRegistry = contentSourceRegistry,
        hatchet = hatchet,
    )
}
