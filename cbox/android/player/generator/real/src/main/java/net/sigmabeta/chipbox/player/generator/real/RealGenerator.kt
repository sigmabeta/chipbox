package net.sigmabeta.chipbox.player.generator.real

import android.content.Context
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
 * Production [Generator]. Wires up a [RealPcmTrackSourceFactory] over the supplied list of
 * native emulators (selected per-track by file extension) and a `pcm-cache/` directory under
 * the app's cache directory for render-ahead PCM output.
 *
 * The factory itself owns input staging (writing source bytes + chain files to a real path
 * the native code can read) and the cache file format. This class is intentionally a thin
 * wiring layer — most of what used to live here moved into the cache module.
 */
class RealGenerator(
    repository: Repository,
    contentSourceRegistry: ContentSourceRegistry,
    bufferManager: ProducerBufferManager,
    emulators: List<Emulator>,
    context: Context,
    hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Generator(repository, contentSourceRegistry, bufferManager, hatchet, dispatcher) {

    override val pcmSourceFactory: PcmTrackSource.Factory = RealPcmTrackSourceFactory(
        emulators = emulators,
        stagingDir = File(context.cacheDir, "playback"),
        pcmCacheDir = File(context.cacheDir, "pcm-cache"),
        contentSourceRegistry = contentSourceRegistry,
        hatchet = hatchet,
    )
}
