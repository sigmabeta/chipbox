package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.sage.logging.Hatchet

/**
 * [PcmTrackSource] backed by a complete cached `.pcm` file. The fast path: no emulation, just
 * random-access file IO.
 *
 * Used when [RealPcmTrackSourceFactory] finds an existing complete cache entry for a track —
 * replays, second-plays after a previous render-ahead completed, anything that doesn't need
 * the emulator at all.
 *
 * Bumps the file's mtime on construction so the [PcmCacheJanitor]'s LRU policy treats this
 * read as a fresh access.
 */
internal class CachedFilePcmSource(
    private val reader: PcmCacheFile.Reader,
    private val trackTitle: String,
    private val hatchet: Hatchet,
) : PcmTrackSource {

    @Volatile
    private var cursor: Long = 0L

    @Volatile
    private var lastError: String? = null

    override val sampleRate: Int = reader.sampleRate

    override val totalFrames: Long? = reader.totalFrames

    override val isOver: Boolean get() = cursor >= reader.totalFrames

    override val peakAmplitude: Int = reader.header.peakAmplitude

    init {
        reader.touch()
        hatchet.d("Opened cached PCM source: ${reader.totalFrames} frames at ${reader.sampleRate} Hz.")
        // Replay the headroom figure measured when this track was first rendered (stashed in
        // the cache header), so a cache hit reports the same line a fresh render would.
        LoudnessLog.report(hatchet, trackTitle, reader.header.peakAmplitude)
    }

    override suspend fun readFrames(buffer: ShortArray): Int {
        val read = try {
            reader.readFrames(buffer, cursor)
        } catch (t: Throwable) {
            lastError = "Cache read failure: ${t.message}"
            return 0
        }
        cursor += read.toLong()
        return read
    }

    override suspend fun seek(framePosition: Long) {
        cursor = framePosition.coerceAtLeast(0L)
    }

    override fun getLastError(): String? = lastError

    override suspend fun close() {
        reader.close()
    }
}
