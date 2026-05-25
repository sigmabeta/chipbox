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
 * Production [Generator] for both targets. Wires a [RealPcmTrackSourceFactory] over the
 * supplied native emulators (selected per-track by file extension); the factory owns input
 * staging and the render-ahead PCM cache (the real work lives in the pure-JVM
 * `:cbox:common:player:cache:real`).
 *
 * Takes the staging / PCM-cache directories as plain [File]s. The Android Hilt module derives
 * them from `Context.cacheDir`; the JVM app passes a work dir. The old Android-only twin's
 * `Context` parameter was never a platform seam — just these two dirs — so one `sage.kmp`
 * module serves both variants.
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
