package net.sigmabeta.chipbox.player.director

/**
 * High-level lifecycle of a playback session as seen by observers of [Director.playbackState].
 *
 * The director derives this enum by reducing events from both the generator and the speaker, so
 * a single value may correspond to different producer/consumer combinations: [BUFFERING], for
 * instance, covers both the initial load before any audio and a mid-track underrun while the
 * generator catches back up.
 */
enum class PlayerState {
    /** No session has started yet in this Director's lifetime. */
    IDLE,

    /** A session ran but has been torn down. Generator and speaker are both stopped. */
    STOPPED,

    /** Speaker is starved: no audio is flowing while it waits for buffers. Covers the initial
     *  load, a mid-track underrun, and the gap while a skipped-to track loads. */
    BUFFERING,

    /** Steady-state playback — audio is flowing. */
    PLAYING,

    /** User-initiated pause. The generator may keep producing buffers; the speaker is stopped. */
    PAUSED,

    /** Speaker is still draining buffered audio for the final track in the setlist. */
    ENDING,

    /** Something went wrong. See [ChipboxPlaybackState.errorMessage]. */
    ERROR
}
