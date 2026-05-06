package net.sigmabeta.chipbox.player.director

/**
 * High-level lifecycle of a playback session as seen by observers of [Director.playbackState].
 *
 * The director derives this enum by reducing events from both the generator and the speaker, so
 * a single value here may correspond to different combinations of producer/consumer activity:
 * for example [PRELOADING] means the speaker is still emitting audio for the *current* track
 * while the generator has begun loading the *next* one.
 */
enum class PlayerState {
    /** No session has started yet in this Director's lifetime. */
    IDLE,

    /** A session ran but has been torn down. Generator and speaker are both stopped. */
    STOPPED,

    /** Speaker is silent and waiting for the generator to fill enough buffers to start. */
    BUFFERING,

    /** Audio is playing; the generator is concurrently loading the next track in the setlist. */
    PRELOADING,

    /** Steady-state playback. */
    PLAYING,

    /** Seeking forward. Should be quick. */
    FAST_FORWARDING,

    /** Seeking backward. Usually takes longer because most emulators can't rewind — they
     *  re-load the track and fast-forward to the target position. */
    REWINDING,

    /** User-initiated pause. The generator may keep producing buffers; the speaker is stopped. */
    PAUSED,

    /** Speaker is still draining buffered audio for the final track in the setlist. */
    ENDING,

    /** Something went wrong. See [ChipboxPlaybackState.errorMessage]. */
    ERROR
}
