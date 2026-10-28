package net.sigmabeta.chipbox.player.persistence

/**
 * Optional pre-restore rewrite of a saved [SessionSnapshot].
 *
 * The Web app persists track ids in browser storage, where a library rebuild or RSN re-keying can
 * invalidate some of them; resolving the ids through the repository before `SessionRequest.Restore`
 * keeps the director from pointing at tracks that no longer exist. Returning null drops the
 * snapshot (the persister clears it) when nothing usable survives.
 *
 * A filter failure must never block launch: the persister falls back to restoring the snapshot
 * as saved when [filter] throws.
 */
fun interface SessionSnapshotFilter {
    /** Return the snapshot to restore (possibly rewritten), or null to drop and clear it. */
    suspend fun filter(snapshot: SessionSnapshot): SessionSnapshot?
}
