package net.sigmabeta.chipbox.player.cache.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path

/**
 * Cache-bypass [PcmTrackSource.Factory]. Same emulator selection and input staging as
 * [RealPcmTrackSourceFactory], but every track gets a live [EmulatorPcmSource] directly —
 * no on-disk read-through, no render-ahead writer, no janitor. Seek is unsupported because
 * the live emulator can't fast-forward (that's the whole reason the cache exists).
 *
 * Intended as a fallback for cases where the cache layer should be skipped: e.g. no writable
 * cache directory, an opt-out setting, or track types we deliberately don't want to persist.
 * Not wired into [net.sigmabeta.chipbox.player.generator.real.RealGenerator] today.
 */
class UncachedPcmTrackSourceFactory(
    private val emulators: List<Emulator>,
    private val stagingDir: Path,
    private val fileSystem: FileSystem,
    private val contentSourceRegistry: ContentSourceRegistry,
    private val hatchet: Hatchet,
    private val emulatorDispatcher: CoroutineDispatcher =
        net.sigmabeta.chipbox.utils.emulatorDispatcher,
) : PcmTrackSource.Factory {

    override suspend fun open(track: Track, bytes: ByteArray): PcmTrackSource {
        val ext = track.extension
        // RSN archives are unpacked to an SPC during staging, so they play through the SPC emulator.
        val emuExt = playbackExtension(ext)
        val emulator = emulators.firstOrNull { it.isFileExtensionSupported(emuExt) }
            ?: throw IllegalArgumentException("No emulator found for extension '$emuExt'.")

        // JS emulators fetch + instantiate their WASM module here on first use; deferring lets
        // the page load skip ~4 MB of upfront WASM transfer. No-op on JVM/Android (the
        // synchronous System.loadLibrary runs in [loadNativeLib] below).
        emulator.ensureNativeLibReady()

        val stagingTrackDir = stagingDir / "track-${track.id}"
        val stagedFile = stageTrack(
            track = track,
            ext = ext,
            mainBytes = bytes,
            stagingTrackDir = stagingTrackDir,
            fileSystem = fileSystem,
            contentSourceRegistry = contentSourceRegistry,
            hatchet = hatchet,
        )

        // Construct on the emulator thread: the constructor's loadTrack call must be serialised
        // with all other native-core access (see EmulatorPcmSource threading note).
        return withContext(emulatorDispatcher) {
            EmulatorPcmSource(
                emulator = emulator,
                track = track.asStagedPlaybackTrack(),
                stagedFile = stagedFile,
                stagingTrackDir = stagingTrackDir,
                fileSystem = fileSystem,
                hatchet = hatchet,
                emulatorDispatcher = emulatorDispatcher,
            )
        }
    }
}
