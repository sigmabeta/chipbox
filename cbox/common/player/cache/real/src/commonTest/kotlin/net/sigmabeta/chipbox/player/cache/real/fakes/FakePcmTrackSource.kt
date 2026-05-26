package net.sigmabeta.chipbox.player.cache.real.fakes

import kotlinx.coroutines.suspendCancellableCoroutine
import net.sigmabeta.chipbox.player.cache.PcmTrackSource

/**
 * Scriptable [PcmTrackSource] for [CachingPcmSource] tests. Each enqueued chunk supplies a fixed
 * number of frames either filled with [Chunk.Audible.pattern] or zeroed for [Chunk.Silent]. When
 * the queue is drained the source either reports end-of-track ([isOver] becomes true) or — if
 * [suspendOnEmpty] is set — suspends in a cancellable way so a test can exercise the writer-loop
 * cancellation path.
 *
 * The writer reads up to [net.sigmabeta.chipbox.player.cache.real.CachingPcmSource]'s
 * `WRITER_BUFFER_FRAMES` (4096) per call; a single enqueued chunk larger than that is split
 * across iterations automatically.
 */
class FakePcmTrackSource(
    override val sampleRate: Int = 44_100,
    override val totalFrames: Long? = null,
) : PcmTrackSource {

    sealed interface Chunk {
        val frames: Int
        data class Audible(override val frames: Int, val pattern: Short = 1_000) : Chunk
        data class Silent(override val frames: Int) : Chunk
    }

    private val chunks: ArrayDeque<Chunk> = ArrayDeque()
    private var endReached: Boolean = false
    private var lastError: String? = null
    private var suspendOnEmpty: Boolean = false

    var closed: Boolean = false
        private set

    fun enqueueAudible(frames: Int, pattern: Short = 1_000) {
        chunks.add(Chunk.Audible(frames, pattern))
    }

    fun enqueueSilent(frames: Int) {
        chunks.add(Chunk.Silent(frames))
    }

    /** Park the writer's next [readFrames] indefinitely when the queue empties — used by tests
     *  that exercise the cancel-mid-render path via [CachingPcmSource.close]. */
    fun parkOnEmpty() {
        suspendOnEmpty = true
    }

    fun setLastError(message: String) {
        lastError = message
    }

    override val isOver: Boolean get() = endReached
    override val loudnessLufs: Double = Double.NaN
    override val truePeakDbtp: Double = Double.NEGATIVE_INFINITY

    override suspend fun readFrames(buffer: ShortArray): Int {
        if (chunks.isEmpty()) {
            if (suspendOnEmpty) {
                // Cancellable park — when the writer job is cancelled, this resumes with
                // CancellationException, which CachingPcmSource handles in its writer's catch.
                suspendCancellableCoroutine<Nothing> { /* never resumed */ }
            }
            endReached = true
            return 0
        }
        val chunk = chunks.removeFirst()
        val maxFrames = buffer.size / 2
        val frames = minOf(chunk.frames, maxFrames)
        when (chunk) {
            is Chunk.Audible -> {
                for (i in 0 until frames) {
                    buffer[i * 2] = chunk.pattern
                    buffer[i * 2 + 1] = chunk.pattern
                }
            }
            is Chunk.Silent -> {
                for (i in 0 until frames * 2) buffer[i] = 0
            }
        }
        // Carry the chunk's tail over to the next call if it didn't fit in this read.
        if (chunk.frames > frames) {
            val rest = chunk.frames - frames
            val tail = when (chunk) {
                is Chunk.Audible -> Chunk.Audible(rest, chunk.pattern)
                is Chunk.Silent -> Chunk.Silent(rest)
            }
            chunks.addFirst(tail)
        }
        return frames
    }

    override suspend fun seek(framePosition: Long) {
        throw UnsupportedOperationException("FakePcmTrackSource doesn't support seek.")
    }

    override fun getLastError(): String? = lastError
    override fun getDiagnostics(): String? = null
    override suspend fun close() { closed = true }
}
