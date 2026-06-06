package net.sigmabeta.chipbox.player.persistence

/**
 * Bridges the [net.sigmabeta.chipbox.player.director.Director] and the [PlaybackSessionStore]:
 * restores the last session on launch and keeps the stored snapshot in step with playback.
 *
 * Wired from the Android playback service (the one place that owns the director's lifecycle) —
 * [restore] runs once on startup, [observe] runs for the service's lifetime, and [snapshotNow]
 * is a teardown safety net for "closed while still playing" (a pause never happened, so the
 * snapshot would otherwise be stale).
 */
interface PlaybackSessionPersister {
    /** Load the saved snapshot, if any, and restore it into the director — loaded but paused at
     *  the saved position. A missing or unreadable snapshot is a no-op (and is cleared). */
    suspend fun restore()

    /** Begin observing playback so the snapshot is written when the user pauses and dropped when
     *  the session stops. Idempotent — a second call is ignored. */
    fun observe()

    /** Write the latest observed playback position immediately, regardless of state — the teardown
     *  net for "closed while playing" (no pause ever happened). Synchronous so it can run from a
     *  service `onDestroy`/`onTaskRemoved` with no coroutine; relies on [observe] already running.
     *  No-op when nothing is playing. */
    fun snapshotNow()
}
