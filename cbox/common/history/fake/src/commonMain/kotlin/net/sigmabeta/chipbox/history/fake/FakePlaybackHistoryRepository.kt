package net.sigmabeta.chipbox.history.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.sigmabeta.chipbox.history.PlayCount
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.history.RecentPlay
import net.sigmabeta.chipbox.models.Track

/**
 * Recording / configurable [PlaybackHistoryRepository] fake. [recordedPlays] holds, in order, every
 * track passed to [recordPlay]; [clearCalls] counts [clearHistory] invocations. The read streams
 * return whatever the test seeds via [recent] / [mostPlayedSongs] / [mostPlayedGames] /
 * [mostPlayedArtists] (each capped at the requested limit).
 */
class FakePlaybackHistoryRepository : PlaybackHistoryRepository {
    val recordedPlays: MutableList<Track> = mutableListOf()
    var clearCalls: Int = 0
        private set

    var recent: List<RecentPlay> = emptyList()
    var mostPlayedSongs: List<PlayCount> = emptyList()
    var mostPlayedGames: List<PlayCount> = emptyList()
    var mostPlayedArtists: List<PlayCount> = emptyList()

    override suspend fun recordPlay(track: Track) {
        recordedPlays += track
    }

    override fun recentlyPlayed(limit: Int): Flow<List<RecentPlay>> = flowOf(recent.take(limit))

    override fun mostPlayedSongs(limit: Int): Flow<List<PlayCount>> = flowOf(mostPlayedSongs.take(limit))

    override fun mostPlayedGames(limit: Int): Flow<List<PlayCount>> = flowOf(mostPlayedGames.take(limit))

    override fun mostPlayedArtists(limit: Int): Flow<List<PlayCount>> = flowOf(mostPlayedArtists.take(limit))

    override suspend fun clearHistory() {
        clearCalls++
        recordedPlays.clear()
    }
}
