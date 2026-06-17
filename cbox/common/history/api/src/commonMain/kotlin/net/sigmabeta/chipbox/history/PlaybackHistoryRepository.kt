package net.sigmabeta.chipbox.history

import net.sigmabeta.chipbox.models.Track

/**
 * Write-side facade over the playback-history database. Records qualifying plays and clears the
 * whole history. Reads (recent / most-played lists) are intentionally not exposed yet — there is
 * no history-viewing UI in this iteration.
 */
interface PlaybackHistoryRepository {
    /**
     * Record one play of [track]: insert a `song_play` row and increment the play counts for the
     * track, its game ([Track.gameId]), and every artist in [Track.artists]. Callers gate on the
     * "played 10+ seconds / reached the end" rule before calling this; the repository just writes.
     */
    suspend fun recordPlay(track: Track)

    /** Wipe all playback history — every play row and every per-song/game/artist counter. */
    suspend fun clearHistory()
}
