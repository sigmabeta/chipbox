package net.sigmabeta.chipbox.player.cache.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmCacheKey
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.emulators.EmulatorSelector
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path

/**
 * Production [PcmTrackSource.Factory]. Decides per-track whether to serve from a complete
 * cached file or to start a render-ahead session backed by a live emulator.
 *
 * Selects the emulator by file extension (matching what `RealGenerator` used to do).
 * Constructs the [PcmCacheKey] from a hash of the source bytes plus chain files; this hash is
 * deliberately the only place where cache identity is decided, so changes to chain-file
 * resolution stay in lockstep with cache invalidation.
 *
 * Stages input via [stageTrack] (shared with [UncachedPcmTrackSourceFactory]): native emulators
 * expect a real filesystem path, so source bytes plus any chain files are written into a
 * per-track directory under [stagingDir] before the emulator is constructed. The directory is
 * cleaned up when the source closes.
 *
 * When the selected emulator advertises a [Emulator.knownSampleRate], the complete-cache check
 * runs before staging or constructing anything, so a warm hit performs no native track load,
 * emulator reset or teardown. Unknown-rate backends keep the negotiate-then-recheck fallback.
 */
class RealPcmTrackSourceFactory(
    private val emulators: List<Emulator>,
    private val stagingDir: Path,
    private val pcmCacheDir: Path,
    private val fileSystem: FileSystem,
    private val contentSourceRegistry: ContentSourceRegistry,
    private val hatchet: Hatchet,
    cacheCapBytes: Long = PcmCacheJanitor.DEFAULT_CAP_BYTES,
    private val emulatorDispatcher: CoroutineDispatcher =
        net.sigmabeta.chipbox.utils.emulatorDispatcher,
) : PcmTrackSource.Factory {

    private val hasher = PcmCacheHasher(contentSourceRegistry)

    private val janitor = PcmCacheJanitor(fileSystem, pcmCacheDir, cacheCapBytes, hatchet)

    init {
        janitor.runStartupCleanup()
    }

    override suspend fun open(track: Track, bytes: ByteArray): PcmTrackSource {
        val ext = track.extension
        // RSN archives are unpacked to an SPC during staging, so they play through the SPC emulator.
        val emuExt = playbackExtension(ext)
        val emulator = EmulatorSelector.select(emulators, emuExt)
            ?: throw IllegalArgumentException("No emulator found for extension '$emuExt'.")

        // JS emulators fetch + instantiate their WASM module here on first use; deferring lets
        // the page load skip ~4 MB of upfront WASM transfer. No-op on JVM/Android. Selection may
        // already have done this for a backend whose extension list needs the module
        // (vgmstream); the call is idempotent.
        emulator.ensureNativeLibReady()

        val sourceHash = hasher.hash(track, bytes)

        fileSystem.createDirectories(pcmCacheDir)

        // Fixed-rate backends know the output rate without loading a track, so a complete cache
        // entry can be served *before* staging or constructing the native emulator. This is the
        // difference between a warm open and a cold one for 2SF: no native load, reset or teardown.
        val knownRate = emulator.knownSampleRate
        if (knownRate != null) {
            val knownKey = PcmCacheKey(
                sourceHash = sourceHash,
                trackNumber = track.trackNumber,
                sampleRate = knownRate,
            )
            val knownReader = PcmCacheFile.openForRead(fileSystem, pcmCacheDir, knownKey)
            if (knownReader != null) {
                hatchet.i(
                    "Cache hit for ${track.title} (${knownKey.filename()}); using CachedFilePcmSource."
                )
                return CachedFilePcmSource(knownReader, track.title, hatchet)
            }
        }

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

        // Construct on the emulator thread: the constructor calls into the native core
        // (loadTrack), which must not run concurrently with another track's generate/teardown of
        // the shared singleton.
        val emulatorSource = withContext(emulatorDispatcher) {
            EmulatorPcmSource(
                emulator = emulator,
                track = track.asStagedPlaybackTrack(),
                stagedFile = stagedFile,
                stagingTrackDir = stagingTrackDir,
                fileSystem = fileSystem,
                hatchet = hatchet,
                emulatorDispatcher = emulatorDispatcher,
                // The cache writer measures every committed buffer (after trailing-silence
                // trimming) and stores/reports that figure; measuring the same frames again inside
                // the emulator source would be a second full BS.1770 pass for an unused value.
                measureLoudness = false,
            )
        }
        val key = PcmCacheKey(
            sourceHash = sourceHash,
            trackNumber = track.trackNumber,
            sampleRate = emulatorSource.sampleRate,
        )
        if (knownRate != null && knownRate != emulatorSource.sampleRate) {
            hatchet.w(
                "Emulator ${emulator::class.simpleName} advertised ${knownRate}Hz for " +
                    "${track.title} but negotiated ${emulatorSource.sampleRate}Hz; rebuilding " +
                    "the cache key from the negotiated rate."
            )
        }

        // Re-check now that the real rate is known: this is the unknown-rate negotiation path and
        // also covers a concurrent writer that sealed the entry while we were staging.
        val reader = PcmCacheFile.openForRead(fileSystem, pcmCacheDir, key)
        if (reader != null) {
            hatchet.i("Cache hit for ${track.title} (${key.filename()}); using CachedFilePcmSource.")
            try {
                emulatorSource.close()
            } catch (t: Throwable) {
                hatchet.w("Error tearing down unused emulator source: ${t.message}")
            }
            return CachedFilePcmSource(reader, track.title, hatchet)
        }

        hatchet.i("Cache miss for ${track.title}; starting render-ahead.")
        val writer = PcmCacheFile.openForWrite(
            fileSystem = fileSystem,
            cacheDir = pcmCacheDir,
            key = key,
            trackId = track.id,
            trackLengthMs = track.trackLengthMs,
        )
        janitor.markInUse(writer.finalPath)
        return CachingPcmSource(
            emulatorSource = emulatorSource,
            writer = writer,
            fileSystem = fileSystem,
            track = track,
            key = key,
            hatchet = hatchet,
            onWriteComplete = {
                janitor.markIdle(writer.finalPath)
                janitor.enforceCap()
            },
            onWriteAbort = {
                janitor.markIdle(writer.finalPath)
            },
        )
    }
}
