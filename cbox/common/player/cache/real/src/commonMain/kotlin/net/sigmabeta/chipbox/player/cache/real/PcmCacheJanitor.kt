package net.sigmabeta.chipbox.player.cache.real

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import net.sigmabeta.chipbox.player.cache.PcmCacheFormat
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Maintains the `pcm-cache/` directory: cleans up partial/corrupt files at startup, enforces
 * an LRU size cap on every successful cache write, and lets the factory mark a file as "in
 * use" so playback's own cache file is never evicted out from under it.
 *
 * Eviction policy is mtime-based — the mtime is bumped on every cache hit (see
 * [PcmCacheFile.Reader.touch]) and there's no portable atime, so mtime is what we have. Worst
 * case is a backup tool that strips mtimes, which we accept. The "in use" set is held behind an
 * atomic reference (immutable set + compare-and-set) so it stays thread-safe without locks.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class PcmCacheJanitor(
    private val fileSystem: FileSystem,
    private val cacheDir: Path,
    private val capBytes: Long,
    private val hatchet: Hatchet,
) {
    private val initialized = AtomicBoolean(false)

    private val inUse = AtomicReference<Set<Path>>(emptySet())

    /** Sweep `.pcm.tmp` files (interrupted writes) and any `.pcm` file whose header is
     *  malformed or marks the body as in-progress. Idempotent; safe to call multiple times,
     *  but only the first call does work. */
    fun runStartupCleanup() {
        if (!initialized.compareAndSet(false, true)) return
        if (fileSystem.metadataOrNull(cacheDir)?.isDirectory != true) return

        var deleted = 0
        for (file in fileSystem.list(cacheDir)) {
            if (fileSystem.metadataOrNull(file)?.isRegularFile != true) continue
            val name = file.name
            when {
                name.endsWith(".pcm.tmp") -> {
                    if (delete(file)) deleted++
                }

                name.endsWith(".pcm") -> {
                    val header = PcmCacheFile.readHeader(fileSystem, file)
                    val isComplete = header != null &&
                        header.completion == PcmCacheFormat.COMPLETION_COMPLETE
                    if (!isComplete) {
                        if (delete(file)) deleted++
                    }
                }
            }
        }
        if (deleted > 0) {
            hatchet.i("PCM cache startup cleanup: deleted $deleted partial/corrupt file(s).")
        }
    }

    fun markInUse(file: Path) = inUse.mutate { it + file }

    fun markIdle(file: Path) = inUse.mutate { it - file }

    /**
     * If the cache exceeds [capBytes], delete completed `.pcm` files in mtime order (oldest
     * first) until we're back under the cap. Files in [inUse] are skipped so the actively
     * playing track's cache survives even if it's the oldest.
     */
    fun enforceCap() {
        if (fileSystem.metadataOrNull(cacheDir)?.isDirectory != true) return

        val protectedFiles = inUse.load()
        val candidates = fileSystem.list(cacheDir)
            .filter {
                it.name.endsWith(".pcm") &&
                    it !in protectedFiles &&
                    fileSystem.metadataOrNull(it)?.isRegularFile == true
            }
            .map { it to (fileSystem.metadataOrNull(it)?.size ?: 0L) }

        var totalSize = candidates.sumOf { it.second }
        if (totalSize <= capBytes) return

        val sorted = candidates.sortedBy { fileSystem.metadataOrNull(it.first)?.lastModifiedAtMillis ?: 0L }
        var evicted = 0
        var bytesEvicted = 0L
        for ((file, size) in sorted) {
            if (totalSize <= capBytes) break
            if (delete(file)) {
                totalSize -= size
                bytesEvicted += size
                evicted++
            }
        }
        if (evicted > 0) {
            hatchet.i(
                "PCM cache evicted $evicted file(s), freed " +
                    "${bytesEvicted / BYTES_PER_KIB / BYTES_PER_KIB} MB " +
                    "to stay under ${capBytes / BYTES_PER_KIB / BYTES_PER_KIB} MB cap."
            )
        }
    }

    private fun delete(file: Path): Boolean = try {
        fileSystem.delete(file)
        true
    } catch (_: IOException) {
        false
    }

    private inline fun AtomicReference<Set<Path>>.mutate(transform: (Set<Path>) -> Set<Path>) {
        while (true) {
            val current = load()
            if (compareAndSet(current, transform(current))) return
        }
    }

    companion object {
        const val DEFAULT_CAP_BYTES: Long = 1024L * 1024L * 1024L

        /** Bytes per kibibyte; applied twice to convert bytes to MiB for logging. */
        private const val BYTES_PER_KIB = 1024
    }
}
