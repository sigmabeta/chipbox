package net.sigmabeta.chipbox.player.cache

import net.sigmabeta.chipbox.models.Track

/**
 * Frame-addressable source of stereo 16-bit PCM for a single track.
 *
 * Replaces the direct emulator coupling the [net.sigmabeta.chipbox.player.generator.Generator]
 * used to have. A source can back its frames with anything — a live native emulator, a complete
 * cached `.pcm` file on disk, or an emulator-with-write-through to a cache file racing ahead of
 * the read pointer. The Generator does not care which.
 *
 * ### Lifecycle
 * Open exactly one [PcmTrackSource] per track via [Factory.open]. The Generator drives reads
 * sequentially via [readFrames] (advancing its own frame counter) and may [seek] at any time.
 * Always close via [close] when done — implementations may hold native handles, file
 * descriptors, or background coroutines.
 *
 * ### Threading
 * All methods may be called from any coroutine, but never concurrently from more than one at a
 * time on a given instance. The Generator's loop is single-threaded; honor that.
 */
interface PcmTrackSource {
    /** The emulator's native sample rate for this track, in Hz. Constant for the source's
     *  lifetime; the Generator caches it after [Factory.open] returns. */
    val sampleRate: Int

    /** Track length in frames, or `null` if unknown. Sources backed by a complete cache file
     *  always know this; live emulator sources may not until generation finishes. */
    val totalFrames: Long?

    /**
     * Fill [buffer] with up to its capacity in stereo frames, starting at the source's current
     * read cursor. Returns the number of frames actually read (0 if end-of-track is reached
     * before any frames could be produced).
     *
     * For caching sources, this may suspend briefly if the writer hasn't produced the requested
     * range yet. Implementations should bound the wait and surface a stalled writer as an
     * exception, not a hang.
     */
    suspend fun readFrames(buffer: ShortArray): Int

    /**
     * Reposition the read cursor to [framePosition]. Subsequent [readFrames] calls produce
     * audio starting at that position. May suspend if the position is past what's currently
     * available (caching sources block on the writer; cached-file sources never block).
     */
    suspend fun seek(framePosition: Long)

    /** True when no further frames will be produced — track has reached its end. The Generator
     *  polls this between buffers to know when to advance the setlist. */
    val isOver: Boolean

    /** Integrated BS.1770 loudness across the track in LUFS, or [Double.NaN] until enough audio
     *  has been measured (the first valid value appears after the first 400 ms). For a render-
     *  ahead source the writer races well past the play head, so the figure is final within the
     *  first buffers; cached-file sources return the value stashed in the header on construction. */
    val loudnessLufs: Double get() = Double.NaN

    /** Inter-sample true peak across the track in dBTP, or [Double.NEGATIVE_INFINITY] if no
     *  audible peak yet. Paired with [loudnessLufs] to compute the loudness-normalization gain
     *  with a peak ceiling so a quiet-but-dynamic track can't be boosted into clipping. */
    val truePeakDbtp: Double get() = Double.NEGATIVE_INFINITY

    /** Most-recent error from the underlying source, or null. Polled by the Generator after
     *  every read; non-null aborts playback. */
    fun getLastError(): String?

    /** Non-fatal diagnostics accumulated during the most recent [readFrames] call. Polled for
     *  logging only — does not abort playback. */
    fun getDiagnostics(): String? = null

    /** Release all resources held by this source. Safe to call multiple times. */
    suspend fun close()

    /**
     * Resolves a [Track] to the [PcmTrackSource] that should produce its audio.
     *
     * The factory is the seam between generic Generator code and the cache module's
     * implementation choices: it inspects what's on disk, hashes the source bytes, and decides
     * whether to return a [CachedFilePcmSource]-style instant-replay reader or a
     * [CachingPcmSource]-style writer-plus-reader pair.
     */
    interface Factory {
        suspend fun open(track: Track, bytes: ByteArray): PcmTrackSource
    }
}
