package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.player.cache.PcmCacheFormat
import net.sigmabeta.sage.logging.Hatchet
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Maintains the `pcm-cache/` directory: cleans up partial/corrupt files at startup, enforces
 * an LRU size cap on every successful cache write, and lets the factory mark a file as "in
 * use" so playback's own cache file is never evicted out from under it.
 *
 * Eviction policy is mtime-based — `File.setLastModified` is bumped on every cache hit (see
 * [PcmCacheFile.Reader.touch]) and Java exposes no portable atime, so mtime is what we have.
 * Worst case is a backup tool that strips mtimes, which we accept.
 */
internal class PcmCacheJanitor(
    private val cacheDir: File,
    private val capBytes: Long,
    private val hatchet: Hatchet,
) {
    private val initialized = AtomicBoolean(false)

    private val inUse = mutableSetOf<File>()

    private val inUseLock = Any()

    /** Sweep `.pcm.tmp` files (interrupted writes) and any `.pcm` file whose header is
     *  malformed or marks the body as in-progress. Idempotent; safe to call multiple times,
     *  but only the first call does work. */
    fun runStartupCleanup() {
        if (!initialized.compareAndSet(false, true)) return
        if (!cacheDir.isDirectory) return

        var deleted = 0
        cacheDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val name = file.name
            when {
                name.endsWith(".pcm.tmp") -> {
                    if (file.delete()) deleted++
                }

                name.endsWith(".pcm") -> {
                    val header = PcmCacheFile.readHeader(file)
                    val isComplete = header != null &&
                        header.completion == PcmCacheFormat.COMPLETION_COMPLETE
                    if (!isComplete) {
                        if (file.delete()) deleted++
                    }
                }
            }
        }
        if (deleted > 0) {
            hatchet.i("PCM cache startup cleanup: deleted $deleted partial/corrupt file(s).")
        }
    }

    fun markInUse(file: File) {
        synchronized(inUseLock) { inUse.add(file) }
    }

    fun markIdle(file: File) {
        synchronized(inUseLock) { inUse.remove(file) }
    }

    /**
     * If the cache exceeds [capBytes], delete completed `.pcm` files in mtime order (oldest
     * first) until we're back under the cap. Files in [inUse] are skipped so the actively
     * playing track's cache survives even if it's the oldest.
     */
    fun enforceCap() {
        if (!cacheDir.isDirectory) return

        val protectedFiles = synchronized(inUseLock) { inUse.toSet() }
        val candidates = cacheDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".pcm") && it !in protectedFiles }
            ?.map { it to it.length() }
            .orEmpty()

        var totalSize = candidates.sumOf { it.second }
        if (totalSize <= capBytes) return

        val sorted = candidates.sortedBy { it.first.lastModified() }
        var evicted = 0
        var bytesEvicted = 0L
        for ((file, size) in sorted) {
            if (totalSize <= capBytes) break
            if (file.delete()) {
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

    companion object {
        const val DEFAULT_CAP_BYTES: Long = 1024L * 1024L * 1024L

        /** Bytes per kibibyte; applied twice to convert bytes to MiB for logging. */
        private const val BYTES_PER_KIB = 1024
    }
}
