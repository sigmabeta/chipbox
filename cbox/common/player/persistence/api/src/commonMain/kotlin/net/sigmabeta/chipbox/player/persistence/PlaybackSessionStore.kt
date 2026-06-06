package net.sigmabeta.chipbox.player.persistence

/**
 * Reads and writes the single persisted [SessionSnapshot] (last-played session) to durable
 * storage. Backed by DataStore in production; an in-memory fake stands in for tests.
 *
 * There is at most one snapshot: a new [save] overwrites the previous one, and [clear] drops it
 * (e.g. once a setlist plays to its end, leaving nothing to resume).
 */
interface PlaybackSessionStore {
    /** The saved snapshot, or null if none has been stored (or it was [clear]ed). */
    suspend fun load(): SessionSnapshot?

    /** Persist [snapshot], replacing any previously stored one. Fire-and-forget, mirroring the
     *  settings managers' write semantics. */
    fun save(snapshot: SessionSnapshot)

    /** Drop the saved snapshot so the next [load] returns null. */
    fun clear()
}
