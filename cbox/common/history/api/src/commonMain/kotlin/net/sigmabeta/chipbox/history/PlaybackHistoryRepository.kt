package net.sigmabeta.chipbox.history

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.models.Track

/**
 * Facade over the playback-history database. Records qualifying plays, exposes the recent /
 * most-played read streams the Home modules surface, and clears the whole history.
 */
interface PlaybackHistoryRepository {
    /**
     * Record one play of [track]: insert a `song_play` row and increment the play counts for the
     * track, its game ([Track.gameId]), and every artist in [Track.artists]. Callers gate on the
     * "played 10+ seconds / reached the end" rule before calling this; the repository just writes.
     */
    suspend fun recordPlay(track: Track)

    /** Most-recently-played distinct tracks, newest first, capped at [limit]. */
    fun recentlyPlayed(limit: Int): Flow<List<RecentPlay>>

    /** Most-played tracks (playCount > 1), highest count first, capped at [limit]. */
    fun mostPlayedSongs(limit: Int): Flow<List<PlayCount>>

    /** Most-played games (playCount > 1), highest count first, capped at [limit]. */
    fun mostPlayedGames(limit: Int): Flow<List<PlayCount>>

    /** Most-played artists (playCount > 1), highest count first, capped at [limit]. */
    fun mostPlayedArtists(limit: Int): Flow<List<PlayCount>>

    /** Wipe all playback history — every play row and every per-song/game/artist counter. */
    suspend fun clearHistory()
}
