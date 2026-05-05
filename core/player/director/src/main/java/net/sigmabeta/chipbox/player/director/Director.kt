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
 * Audio focus hooks ([pauseTemporarily], [duck], [resumeFocus]) are kept distinct from user
 * intent ([play], [pause], [stop]) so transient OS events don't get conflated with the user
 * actually pausing — the resulting state restoration differs.
 */
interface Director {
    // Controls

    /** Begin a new playback session. Resolves the starting track from the [Session] and kicks
     *  off the generator + speaker pipeline. Replaces any previous session. */
    fun start(session: Session)

    /** Resume the current session if paused, or (re)attach the speaker to the buffer stream. */
    fun play()

    /** Stop the speaker but keep the generator's loaded track and buffer state intact. */
    fun pause()

    /** Tear down both speaker and generator. The session is effectively over. */
    fun stop()

    // State Updates

    /** Hot stream of the currently-playing [Track], emitted whenever the active track changes. */
    fun metadataState(): SharedFlow<Track>

    /** Hot stream of the reduced [ChipboxPlaybackState] derived from generator + speaker events. */
    fun playbackState(): SharedFlow<ChipboxPlaybackState>

    // Audio Focus

    /** Transient pause for audio-focus loss. Distinct from [pause] so focus-resume can restore
     *  cleanly without colliding with user intent. */
    fun pauseTemporarily()

    /** Lower output volume for transient focus loss (e.g. nav prompt). 🦆 */
    fun duck()

    /** Undo [pauseTemporarily] / [duck] when audio focus returns. */
    fun resumeFocus()
}
