package net.sigmabeta.chipbox.js.generator

import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.cache.real.UncachedPcmTrackSourceFactory
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.generator.BaseGenerator
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path

/**
 * Browser-side [BaseGenerator] that skips the render-ahead PCM cache. The cache only earns its
 * keep against real disk where re-decoding a long PSF/USF would otherwise cost seconds per
 * playthrough — in the browser the "filesystem" is `okio.FakeFileSystem` (an in-memory map that
 * doesn't survive a refresh), and the WASM emulators decode quickly enough that re-running
 * GME on every play is free. Cache also fights us via FakeFileSystem's "file is open" rejection
 * when [net.sigmabeta.chipbox.player.cache.real.CachingPcmSource] tries to `atomicMove` a `.tmp`
 * whose write sink is still active.
 *
 * Otherwise identical to the JVM/Android [net.sigmabeta.chipbox.player.generator.real.RealGenerator]:
 * supplies an [UncachedPcmTrackSourceFactory] to [BaseGenerator] and lets the base class drive
 * the production buffer / fade / loudness / track-transition machinery unchanged.
 */
class WasmGenerator(
    repository: Repository,
    contentSourceRegistry: ContentSourceRegistry,
    bufferManager: ProducerBufferManager,
    emulators: List<Emulator>,
    stagingDir: Path,
    fileSystem: FileSystem,
    hatchet: Hatchet,
) : BaseGenerator(repository, contentSourceRegistry, bufferManager, hatchet) {

    override val pcmSourceFactory: PcmTrackSource.Factory = UncachedPcmTrackSourceFactory(
        emulators = emulators,
        stagingDir = stagingDir,
        fileSystem = fileSystem,
        contentSourceRegistry = contentSourceRegistry,
        hatchet = hatchet,
    )
}
