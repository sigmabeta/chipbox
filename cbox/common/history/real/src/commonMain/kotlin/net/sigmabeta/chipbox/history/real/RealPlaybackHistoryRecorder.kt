package net.sigmabeta.chipbox.history.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.history.PlaybackHistoryRecorder
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production [PlaybackHistoryRecorder].
 *
 * Combines the director's metadata / playback / session streams and records one play per
 * *track-start*. A track-start is identified by the [TrackInstance] key `(trackId, sessionId)` —
 * so navigating to a different track (or starting a new session) re-arms recording, while a plain
 * seek (which changes neither) does not.
 *
 * A play is recorded when, for the armed instance:
 *  - the position reaches [THRESHOLD_MS] (10s); or
 *  - the track reaches its **natural end** without having recorded yet — detected by the highest
 *    position seen for the instance reaching the track length. Tracking the *max* position (not the
 *    last) makes this robust to the position resetting to 0 as the next track loads, and
 *    distinguishes a full playthrough of a short track from an early skip (whose max stays below
 *    the length).
 *
 * Known limitation (deferred): a repeat-one loop, or the same track twice consecutively in a
 * setlist, keeps the same key, so only the first play is recorded.
 */
class RealPlaybackHistoryRecorder(
    private val director: Director,
    private val repository: PlaybackHistoryRepository,
    private val hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PlaybackHistoryRecorder {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var observing = false

    /** Cancel the scope. Unused in production (the singleton lives for the process); here so tests
     *  can tear the collector down. */
    fun release() {
        scope.cancel()
    }

    override fun observe() {
        if (observing) return
        observing = true

        scope.launch {
            // State for the currently-armed track instance.
            var armedKey: TrackInstance? = null
            var armedTrack: Track? = null
            var maxPosition = 0L
            var recorded = false

            combine(
                director.metadataState(),
                director.playbackState(),
                director.sessionState(),
            ) { track, playback, session ->
                val key = track?.let { TrackInstance(it.id, session?.id) }
                Slice(key, track, playback.state, playback.position)
            }.collect { slice ->
                // Track-start change: settle the outgoing instance (natural-end rule) before re-arming.
                if (slice.key != armedKey) {
                    if (!recorded) recordIfReachedEnd(armedTrack, maxPosition)
                    armedKey = slice.key
                    armedTrack = slice.track
                    recorded = false
                    // Don't trust this emission's position: at a track change the playback flow can
                    // still hold the *previous* track's position until the speaker resets it, which
                    // would otherwise record the new track instantly. Start the new instance at 0
                    // and let later emissions (the reset + real ticks) accumulate the max.
                    maxPosition = 0L
                    return@collect
                }

                val track = slice.track ?: return@collect
                armedTrack = track
                if (slice.position > maxPosition) maxPosition = slice.position

                if (recorded) return@collect

                when {
                    // 10s reached during playback.
                    maxPosition >= THRESHOLD_MS -> {
                        record(track)
                        recorded = true
                    }

                    // Track ended in place (last track of a setlist drains to STOPPED/ENDING) and it
                    // actually played through — record regardless of length.
                    (slice.state == PlayerState.ENDING || slice.state == PlayerState.STOPPED) &&
                        reachedEnd(track, maxPosition) -> {
                        record(track)
                        recorded = true
                    }
                }
            }
        }
    }

    private suspend fun recordIfReachedEnd(track: Track?, maxPosition: Long) {
        if (track != null && reachedEnd(track, maxPosition)) record(track)
    }

    private fun reachedEnd(track: Track, maxPosition: Long): Boolean =
        track.trackLengthMs > 0L && maxPosition >= track.trackLengthMs

    private suspend fun record(track: Track) {
        try {
            repository.recordPlay(track)
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            hatchet.w("Failed to record playback history for track ${track.id}: $t")
        }
    }

    /** Identifies one play of a track. Two plays of the same track id are distinct instances when
     *  they belong to different sessions; navigating away and back (through a different track)
     *  flips the key and re-arms. */
    private data class TrackInstance(
        val trackId: Long,
        val sessionId: Long?,
    )

    /** The slice of the three observed streams the recorder reasons over, combined into one value. */
    private data class Slice(
        val key: TrackInstance?,
        val track: Track?,
        val state: PlayerState,
        val position: Long,
    )

    private companion object {
        private const val THRESHOLD_MS = 10_000L
    }
}
