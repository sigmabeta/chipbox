package net.sigmabeta.chipbox.player.director

import kotlinx.coroutines.flow.SharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.RepeatMode
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
 * Audio focus hooks ([pauseTemporarily], [duck], [resumeFocus]) are kept distinct from user
 * intent ([play], [pause], [stop]) so transient OS events don't get conflated with the user
 * actually pausing — the resulting state restoration differs.
 */
interface Director {
    // Controls

    /** Begin a new playback session. Resolves the starting track from the [Session] and kicks
     *  off the generator + speaker pipeline. Replaces any previous session. */
    fun start(session: Session)

    /**
     * Begin a new playback session from an explicit, caller-supplied setlist (an ordered list
     * of track ids) rather than a repository-resolved collection. Playback starts at
     * [startingPosition] within [setlist]. [sourceName] is a human-readable label for where
     * the setlist came from (e.g. the search query), surfaced on the now-playing screen since
     * an ad-hoc setlist has no backing collection to derive a name from. Use this for ad-hoc
     * queues such as a list of search results that don't correspond to any single
     * game/artist/playlist. Replaces any previous session.
     */
    fun start(
        setlist: List<Long>,
        startingPosition: Int,
        sourceName: String? = null,
        shuffled: Boolean = false,
    )

    /** Resume the current session if paused, or (re)attach the speaker to the buffer stream. */
    fun play()

    /** Stop the speaker but keep the generator's loaded track and buffer state intact. */
    fun pause()

    /** Tear down both speaker and generator. The session is effectively over. */
    fun stop()

    /**
     * Reposition playback within the current track. Free for tracks served from the PCM cache
     * (instant cursor move); briefly buffers when seeking past the writer's watermark for
     * tracks still being rendered.
     */
    fun seek(positionMs: Long)

    /**
     * Advance to the next track in the current setlist. No-op when
     * [ChipboxPlaybackState.skipForwardAllowed] is false (last track in the setlist) or there's
     * no active session. Unlike the generator-driven auto-advance, this does *not* transition to
     * [PlayerState.ENDING] when called at the last track — it just no-ops.
     */
    fun skipForward()

    /**
     * "Back" semantics matching standard music players: if the current track has played past a
     * small threshold (a few seconds), seek to 0; else if we're not on the first track in the
     * setlist, advance to the previous track; else seek to 0. No-op when there's no active
     * session.
     */
    fun skipBack()

    /**
     * Toggle shuffle on the current session. Re-resolves the setlist (original order from the
     * repository, optionally shuffled) and updates `currentPosition` to wherever the active
     * track lands in the new order — playback of the active track is not interrupted; only the
     * sequence of *future* tracks changes. No-op if there's no active session or if shuffle is
     * already in the requested mode.
     */
    fun setShuffled(shuffled: Boolean)

    /**
     * Set the repeat behaviour for the current session. Takes effect on the next generator-driven
     * track change: [RepeatMode.ONE] restarts the current track when it ends, [RepeatMode.ALL]
     * wraps back to the first track after the last one, [RepeatMode.OFF] plays through and stops.
     * Does not interrupt the track currently playing; only changes what happens when it finishes.
     * No-op if there's no active session or if the mode is already set to [mode].
     */
    fun setRepeatMode(mode: RepeatMode)

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
     * Hot stream of playback errors — both recoverable (the failed track was skipped and
     * playback continued) and fatal (the session stopped). One [PlayerErrorEvent] is emitted per
     * occurrence with no replay, so observers see only errors that happen while subscribed; each
     * carries the resolved [Track] the error is attributed to so consumers don't have to guess
     * from [metadataState] (which can lag the actual erroring track). Distinct from
     * [ChipboxPlaybackState.errorMessage], which holds the single latest fatal error.
     */
    fun errorEvents(): SharedFlow<PlayerErrorEvent>

    // Audio Focus

    /** Transient pause for audio-focus loss. Distinct from [pause] so focus-resume can restore
     *  cleanly without colliding with user intent. */
    fun pauseTemporarily()

    /** Duck output volume to 50% for transient focus loss it's OK to play quietly through
     *  (e.g. a navigation prompt). Playback continues; only the volume drops. Independent of
     *  any [setVolume] the user has set. 🦆 */
    fun duck()

    /** Undo [pauseTemporarily] / [duck] when audio focus returns. */
    fun resumeFocus()

    // Volume

    /**
     * Apply an arbitrary master output volume [scale], independent of the end-of-track fade-out
     * and of OS ducking. `1.0` leaves audio unchanged, `1.5` boosts it by 50%, `0.0` silences
     * it; negative values are clamped to `0.0`. No UI is wired to this yet — API only.
     */
    fun setVolume(scale: Double)
}
