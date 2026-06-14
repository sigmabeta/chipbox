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
     * Frames already rendered to local storage and immediately readable without re-running the
     * emulator — the "cached portion" of the track. Snapshotted by the generator each buffer
     * cycle and surfaced through [net.sigmabeta.chipbox.player.director.ChipboxPlaybackState]
     * so the now-playing screen can show how much of the current track is on disk.
     *
     * Cached-file sources return [totalFrames] (the whole file is already there); live
     * caching sources return their writer's watermark; bare emulator sources have no cache
     * and stay at 0.
     */
    val cachedFrames: Long get() = 0L

    /**
     * Fill [buffer] with up to its capacity in stereo frames, starting at the source's current
     * read cursor. Returns the number of frames actually read.
     *
     * Non-blocking: a return of 0 means "no frames available right now", which the caller
     * disambiguates via [isOver] (end-of-track), [getLastError] (failure), and [awaitingRender]
     * (a render-ahead writer that simply hasn't caught up to the cursor yet). The source never
     * decides *how long* to wait for a slow writer — it returns promptly and lets the caller
     * (and ultimately the director's stall guard) own that policy.
     */
    suspend fun readFrames(buffer: ShortArray): Int

    /**
     * Reposition the read cursor to [framePosition]. Subsequent [readFrames] calls produce
     * audio starting at that position. Non-blocking even when the position is past what's been
     * rendered so far — the following [readFrames] simply reports [awaitingRender] until the
     * writer catches up.
     */
    suspend fun seek(framePosition: Long)

    /** True when no further frames will be produced — track has reached its end. The Generator
     *  polls this between buffers to know when to advance the setlist. */
    val isOver: Boolean

    /**
     * True when [readFrames] is returning 0 only because the source hasn't yet rendered the
     * frames under the read cursor — a render-ahead writer that hasn't caught up (e.g. right
     * after a seek into an un-rendered region), as opposed to a genuine end-of-track or error.
     * The generator polls this to know it should keep waiting (and report render progress)
     * rather than abort, while leaving the actual stall timeout to the director. Always false for
     * sources that never lag the cursor (complete cache files, bare emulators).
     */
    val awaitingRender: Boolean get() = false

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
        /**
         * Whether the generator must fetch the track's file bytes (via its content source) before
         * calling [open]. Sources that synthesize audio from the track alone (e.g. the dev synth)
         * set this false, so playback works for tracks with no real backing file — [open] then
         * receives an empty array.
         */
        val requiresContent: Boolean get() = true

        suspend fun open(track: Track, bytes: ByteArray): PcmTrackSource
    }
}
