package net.sigmabeta.chipbox.history.fake

import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.models.Track

/**
 * Recording [PlaybackHistoryRepository] fake. [recordedPlays] holds, in order, every track passed
 * to [recordPlay]; [clearCalls] counts [clearHistory] invocations (and empties [recordedPlays]).
 */
class FakePlaybackHistoryRepository : PlaybackHistoryRepository {
    val recordedPlays: MutableList<Track> = mutableListOf()
    var clearCalls: Int = 0
        private set

    override suspend fun recordPlay(track: Track) {
        recordedPlays += track
    }

    override suspend fun clearHistory() {
        clearCalls++
        recordedPlays.clear()
    }
}
