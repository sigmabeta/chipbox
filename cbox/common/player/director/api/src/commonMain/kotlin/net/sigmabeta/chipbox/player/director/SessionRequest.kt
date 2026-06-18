package net.sigmabeta.chipbox.player.director

import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.Session

/**
 * A request submitted to the [Director] via [Director.request] — every way the rest of the app
 * asks the playback stack to do something. Reifying these (instead of calling methods directly)
 * makes the full control surface one inspectable, recordable type.
 *
 * Audio-focus requests ([PauseTemporarily], [Duck], [ResumeFocus]) are kept distinct from user
 * intent ([Play], [Pause], [Stop]) so transient OS events don't get conflated with the user
 * actually pausing — the resulting state restoration differs.
 */
sealed interface SessionRequest {

    /** Begin a new playback session. Resolves the starting track from the [session] and kicks off
     *  the generator + speaker pipeline. Replaces any previous session. */
    data class Start(val session: Session) : SessionRequest

    /**
     * Begin a new session from an explicit, caller-supplied [setlist] (ordered track ids) rather
     * than a repository-resolved collection. Playback starts at [startingPosition]. [sourceName]
     * is a human-readable label for where the setlist came from (e.g. the search query), surfaced
     * on the now-playing screen since an ad-hoc setlist has no backing collection to name it.
     * Replaces any previous session.
     */
    data class StartSetlist(
        val setlist: List<Long>,
        val startingPosition: Int,
        val sourceName: String? = null,
        val shuffled: Boolean = false,
    ) : SessionRequest

    /**
     * Restore a previously-saved [session] without auto-playing: resolves the setlist, loads the
     * track the session points at, and lands paused showing [positionMs] — the speaker never
     * starts, so launch is silent. The saved offset is applied as a seek the first time the user
     * hits [Play]. Used on app launch to bring the last session back.
     */
    data class Restore(val session: Session, val positionMs: Long) : SessionRequest

    /** Resume the current session if paused, or (re)attach the speaker to the buffer stream. */
    data object Play : SessionRequest

    /** Stop the speaker but keep the generator's loaded track and buffer state intact. */
    data object Pause : SessionRequest

    /** Tear down both speaker and generator. The session is effectively over. */
    data object Stop : SessionRequest

    /** Reposition playback within the current track to [positionMs]. */
    data class Seek(val positionMs: Long) : SessionRequest

    /** Advance to the next track in the current setlist. No-op at the last track / no session. */
    data object SkipForward : SessionRequest

    /** "Back" semantics: seek to 0 if past a small threshold, else the previous track. */
    data object SkipBack : SessionRequest

    /**
     * Jump playback to the track at [position] in the current setlist. Switches the generator and
     * speaker over to that track like a skip. No-op when there's no session, [position] is out of
     * range, or it's already the active position.
     */
    data class PlayPosition(val position: Int) : SessionRequest

    /**
     * Move the track at [fromIndex] to [toIndex] within the current setlist, mutating the live
     * in-memory order for the session (not persisted to the library). The currently-playing track
     * keeps playing — its position is re-indexed so it stays active wherever it lands. No-op when
     * there's no session or either index is out of range.
     */
    data class Reorder(val fromIndex: Int, val toIndex: Int) : SessionRequest

    /**
     * Remove the track at [index] from the current setlist. The currently-playing track keeps
     * playing — its position is re-indexed. No-op when there's no session, [index] is out of range,
     * or [index] is the playing position (the active track isn't removable; callers gate this).
     */
    data class RemoveTrack(val index: Int) : SessionRequest

    /** Toggle shuffle on the current session, re-resolving the setlist around the active track. */
    data class SetShuffled(val shuffled: Boolean) : SessionRequest

    /** Set the repeat behaviour ([RepeatMode]) for the current session; takes effect at the next
     *  track change. */
    data class SetRepeatMode(val mode: RepeatMode) : SessionRequest

    /** Transient pause for audio-focus loss. Distinct from [Pause] so focus-resume restores cleanly. */
    data object PauseTemporarily : SessionRequest

    /** Duck output volume to 50% for transient focus loss it's OK to play quietly through. 🦆 */
    data object Duck : SessionRequest

    /** Undo [PauseTemporarily] / [Duck] when audio focus returns. */
    data object ResumeFocus : SessionRequest

    /** Apply a master output volume [scale] (1.0 = unchanged; negatives clamp to 0.0). */
    data class SetVolume(val scale: Double) : SessionRequest
}
