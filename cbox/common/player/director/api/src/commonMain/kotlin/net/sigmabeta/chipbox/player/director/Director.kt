package net.sigmabeta.chipbox.player.director

import kotlinx.coroutines.flow.SharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session

/**
 * Top of the playback stack. Coordinates the [net.sigmabeta.chipbox.player.generator.Generator]
 * (which produces PCM audio from emulators) and the [net.sigmabeta.chipbox.player.speaker.Speaker]
 * (which consumes it) so the rest of the app never touches either directly.
 *
 * The Director owns the current [Session] and setlist, reduces events from both sides of the
 * pipeline into a single [ChipboxPlaybackState], and exposes that state plus track metadata as
 * cold flows for the UI / media-session layer to observe.
 *
 * Callers don't invoke control methods directly — they submit a [SessionRequest] to [request]
 * (playback control, session lifecycle, and audio focus all flow through that one seam), and
 * observe the result via the state flows below.
 */
interface Director {
    /**
     * Submit a [request] to act on the current session — playback control ([SessionRequest.Play] /
     * [SessionRequest.Pause] / …), session lifecycle ([SessionRequest.Start] / …), or audio focus.
     * See [SessionRequest] for the full set.
     */
    fun request(request: SessionRequest)

    // State Updates

    /** Hot stream of the currently-playing [Track], emitted whenever the active track changes.
     *  Emits `null` before any track has loaded and on session teardown so subscribers see a
     *  meaningful "nothing playing" state instead of waiting indefinitely. */
    fun metadataState(): SharedFlow<Track?>

    /** Hot stream of the reduced [ChipboxPlaybackState] derived from generator + speaker events. */
    fun playbackState(): SharedFlow<ChipboxPlaybackState>

    /**
     * Hot stream of the current [Session]. Emits `null` before [start] is first called and on
     * teardown, and re-emits a fresh copy whenever the director advances within the setlist
     * (so subscribers see updated `currentPosition` values).
     */
    fun sessionState(): SharedFlow<Session?>

    /**
     * Hot stream of the current setlist as ordered track ids — the live play queue the director
     * resolved for the session, whatever the [net.sigmabeta.chipbox.player.common.SessionType].
     * Emits an empty list before any session and on teardown, and re-emits whenever the order
     * changes (shuffle, reorder). The setlist-management UI observes this and resolves the ids to
     * [Track] metadata itself.
     */
    fun setlistState(): SharedFlow<List<Long>>

    /**
     * Hot stream of playback errors — both recoverable (the failed track was skipped and
     * playback continued) and fatal (the session stopped). One [PlayerErrorEvent] is emitted per
     * occurrence with no replay, so observers see only errors that happen while subscribed; each
     * carries the resolved [Track] the error is attributed to so consumers don't have to guess
     * from [metadataState] (which can lag the actual erroring track). Distinct from
     * [ChipboxPlaybackState.errorMessage], which holds the single latest fatal error.
     */
    fun errorEvents(): SharedFlow<PlayerErrorEvent>
}
