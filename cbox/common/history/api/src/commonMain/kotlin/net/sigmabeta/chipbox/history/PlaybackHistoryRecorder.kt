package net.sigmabeta.chipbox.history

/**
 * Observes playback and records a play to the history database once a track has been listened to
 * for long enough. Wired from the one place that owns the director's lifecycle (the Android
 * playback service / the desktop `Main`), mirroring
 * [net.sigmabeta.chipbox.player.persistence.PlaybackSessionPersister].
 */
interface PlaybackHistoryRecorder {
    /**
     * Begin observing the director's playback streams for the rest of the process's life. A play
     * is recorded once per track-start when the track's position reaches 10 seconds, or when the
     * track reaches its natural end without having been recorded yet (covering tracks shorter than
     * 10 seconds). Idempotent — a second call is ignored.
     */
    fun observe()
}
