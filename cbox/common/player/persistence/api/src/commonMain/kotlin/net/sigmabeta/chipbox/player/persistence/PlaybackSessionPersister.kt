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
     *  No-op when nothing is playing.
     *
     *  [teardown] guards the session against the `STOPPED` a released player emits during teardown
     *  (which would otherwise clear what this just wrote). Pass false for a non-teardown flush —
     *  e.g. the web host's `visibilitychange=hidden` — so a subsequent real stop still clears. */
    fun snapshotNow(teardown: Boolean = true)

    /**
     * Undo a teardown suppression left behind by [snapshotNow] when the host did not actually
     * terminate. The web host calls this from `pageshow`: `pagehide` fires for the back-forward
     * cache too, and a page restored from BFCache must still clear its snapshot on a real stop.
     * Hosts with a genuine teardown (the Android service) have nothing to undo; the default is a
     * no-op.
     */
    fun resumeAfterTeardown() {}
}
