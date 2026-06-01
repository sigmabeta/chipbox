package net.sigmabeta.chipbox.player.generator

/**
 * Things the [Generator]'s loop tells the [net.sigmabeta.chipbox.player.director.Director] about
 * as it produces audio. Consumed via [Generator.events].
 */
sealed class GeneratorEvent {
    /** The generator hit a fatal error and its loop has terminated. */
    data class Error(
        val message: String
    ) : GeneratorEvent()

    /** A new track has been requested and the generator is loading it. Emitted before the
     *  emulator actually starts producing samples for [trackId]. */
    data class Loading(
        val trackId: Long
    ) : GeneratorEvent()

    /** A buffer has just been pushed downstream. Used by the director to flip
     *  [net.sigmabeta.chipbox.player.director.PlayerState.BUFFERING] → `PLAYING`. [producedMs]
     *  is the generator's high-water mark within the current track — the time-offset of the
     *  last frame just handed to the buffer manager. [trackId] identifies which track those
     *  frames belong to, so the director can ignore stragglers from a track it has skipped past.
     *  [cachedMs] mirrors [net.sigmabeta.chipbox.player.cache.PcmTrackSource.cachedFrames] in ms
     *  so the now-playing UI can show how much of the active track sits in the cache. */
    data class Emitting(
        val producedMs: Long,
        val trackId: Long,
        val cachedMs: Long = 0L,
    ) : GeneratorEvent()

    /** The generator is waiting on the source to render the frames under the play cursor — a
     *  render-ahead writer that hasn't caught up yet, typically a seek into an un-rendered
     *  region. No new audio was produced this cycle; [cachedMs] is how far the writer has
     *  rendered so far (mirrors [Emitting.cachedMs]). The director treats this as a liveness
     *  signal: while [cachedMs] keeps advancing the wait is healthy, but if it stops advancing
     *  the director's stall guard fires. Kept distinct from [Emitting] precisely so a render
     *  wait doesn't read as playback progress. */
    data class Rendering(
        val cachedMs: Long,
    ) : GeneratorEvent()

    /** The current track has finished and the generator is blocked waiting for the next
     *  track id. The director is expected to respond with [Generator.startTrack]. */
    data object TrackChange : GeneratorEvent()
}
