package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmCacheKey
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.sage.logging.Hatchet
import java.io.File

/**
 * Production [PcmTrackSource.Factory]. Decides per-track whether to serve from a complete
 * cached file or to start a render-ahead session backed by a live emulator.
 *
 * Selects the emulator by file extension (matching what `RealGenerator` used to do).
 * Constructs the [PcmCacheKey] from a hash of the source bytes plus chain files; this hash is
 * deliberately the only place where cache identity is decided, so changes to chain-file
 * resolution stay in lockstep with cache invalidation.
 *
 * Owns input staging: native emulators expect a real filesystem path, so source bytes plus
 * any chain files are written into a per-track directory under [stagingDir] before the
 * emulator is constructed. The directory is cleaned up when the source closes.
 */
class RealPcmTrackSourceFactory(
    private val emulators: List<Emulator>,
    private val stagingDir: File,
    private val pcmCacheDir: File,
    private val contentSourceRegistry: ContentSourceRegistry,
    private val hatchet: Hatchet,
    cacheCapBytes: Long = PcmCacheJanitor.DEFAULT_CAP_BYTES,
) : PcmTrackSource.Factory {

    private val hasher = PcmCacheHasher(contentSourceRegistry)

    private val janitor = PcmCacheJanitor(pcmCacheDir, cacheCapBytes, hatchet)

    init {
        janitor.runStartupCleanup()
    }

    override suspend fun open(track: Track, bytes: ByteArray): PcmTrackSource {
        val ext = track.path.substringAfterLast('.', "").lowercase()
        val emulator = emulators.firstOrNull { it.isFileExtensionSupported(ext) }
            ?: throw IllegalArgumentException("No emulator found for extension '$ext'.")

        val sourceHash = hasher.hash(track, bytes)

        val stagingTrackDir = File(stagingDir, "track-${track.id}")
        val stagedFile = stage(track, ext, bytes, stagingTrackDir)

        val emulatorSource = EmulatorPcmSource(
            emulator = emulator,
            track = track,
            stagedFile = stagedFile,
            stagingTrackDir = stagingTrackDir,
            hatchet = hatchet,
        )
        val key = PcmCacheKey(
            sourceHash = sourceHash,
            trackNumber = track.trackNumber,
            sampleRate = emulatorSource.sampleRate,
        )

        pcmCacheDir.mkdirs()
        val reader = PcmCacheFile.openForRead(pcmCacheDir, key)
        if (reader != null) {
            hatchet.i("Cache hit for ${track.title} (${key.filename()}); using CachedFilePcmSource.")
            try {
                emulatorSource.close()
            } catch (t: Throwable) {
                hatchet.w("Error tearing down unused emulator source: ${t.message}")
            }
            return CachedFilePcmSource(reader, hatchet)
        }

        hatchet.i("Cache miss for ${track.title}; starting render-ahead.")
        val writer = PcmCacheFile.openForWrite(
            cacheDir = pcmCacheDir,
            key = key,
            trackId = track.id,
            trackLengthMs = track.trackLengthMs,
        )
        janitor.markInUse(writer.finalPath)
        return CachingPcmSource(
            emulatorSource = emulatorSource,
            writer = writer,
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

    private suspend fun stage(
        track: Track,
        ext: String,
        mainBytes: ByteArray,
        stagingTrackDir: File,
    ): File {
        val dir = stagingTrackDir.apply {
            deleteRecursively()
            mkdirs()
        }
        val mainFile = File(dir, "main.$ext").apply { writeBytes(mainBytes) }

        if (track.chainFiles.isNotEmpty()) {
            val source = contentSourceRegistry.get(track.source)
            if (source == null) {
                hatchet.w(
                    "No content source '${track.source}' — chain files for track ${track.id} skipped."
                )
            } else {
                for (chain in track.chainFiles) {
                    val chainBytes = source.openBytes(chain.uri)
                        ?: error("Failed to read chain file '${chain.filename}' for track ${track.id}.")
                    File(dir, chain.filename).writeBytes(chainBytes)
                    hatchet.v("Staged chain file ${chain.filename} (${chainBytes.size} bytes).")
                }
            }
        }

        hatchet.v(
            "Staged track ${track.id} to ${mainFile.absolutePath} (+${track.chainFiles.size} chain file(s))."
        )
        return mainFile
    }
}
