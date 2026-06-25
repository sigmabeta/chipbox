package net.sigmabeta.chipbox.js.repository

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.history.PlayCount
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.history.RecentPlay
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.sage.logging.Hatchet

/**
 * Browser-backed [PlaybackHistoryRepository] — the JS twin of the Room-backed
 * `RealPlaybackHistoryRepository`, minus a database. The raw play log is persisted as a JSON array
 * in `window.localStorage`, and every read stream (recently-played plus most-played song / game /
 * artist) is derived from it on demand. An in-memory [MutableStateFlow] mirrors the stored log so
 * the Home cards recompose the instant a play is recorded, without re-parsing localStorage.
 *
 * Each [WebPlay] denormalises the facts the four counters need — the track, its game, and its
 * artists — so there are no separate counter tables: the counts are folds over the log. The log is
 * unbounded in principle but tiny in practice (one small JSON object per qualifying play, and only
 * plays past the recorder's 10s / play-to-end threshold are ever recorded).
 */
class LocalStoragePlaybackHistoryRepository(
    private val hatchet: Hatchet,
) : PlaybackHistoryRepository {

    @Serializable
    private data class WebPlay(
        val trackId: Long,
        val gameId: Long,
        val artistIds: List<Long>,
        val timeMs: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val plays = MutableStateFlow(load())

    @OptIn(ExperimentalTime::class)
    override suspend fun recordPlay(track: Track) {
        val play = WebPlay(
            trackId = track.id,
            // gameId is always populated on a real Track (non-null FK); 0 means "unset".
            gameId = track.gameId,
            artistIds = track.artists?.map { it.id } ?: emptyList(),
            timeMs = Clock.System.now().toEpochMilliseconds(),
        )
        val updated = plays.value + play
        persist(updated)
        plays.value = updated
        hatchet.d("Recorded web play: track=${track.id} game=${track.gameId} artists=${play.artistIds.size}")
    }

    override fun recentlyPlayed(limit: Int): Flow<List<RecentPlay>> = plays.map { log ->
        log.groupBy { it.trackId }
            .map { (id, entries) -> RecentPlay(trackId = id, timeMs = entries.maxOf { it.timeMs }) }
            .sortedByDescending { it.timeMs }
            .take(limit)
    }

    override fun mostPlayedSongs(limit: Int): Flow<List<PlayCount>> =
        mostPlayed(limit) { listOf(it.trackId) }

    override fun mostPlayedGames(limit: Int): Flow<List<PlayCount>> =
        mostPlayed(limit) { if (it.gameId != 0L) listOf(it.gameId) else emptyList() }

    override fun mostPlayedArtists(limit: Int): Flow<List<PlayCount>> =
        mostPlayed(limit) { it.artistIds }

    override suspend fun clearHistory() {
        persist(emptyList())
        plays.value = emptyList()
        hatchet.i("Cleared web playback history.")
    }

    /**
     * Shared most-played fold. [idsOf] projects each play to the ids it increments — the track
     * itself, its game, or each of its artists — so one play of a multi-artist track bumps every
     * credited artist, exactly as `RealPlaybackHistoryRepository.recordPlay` does. Mirrors the SQL
     * `WHERE playCount > 1 ORDER BY playCount DESC, lastPlayedMs DESC` the Room DAOs apply.
     */
    private fun mostPlayed(limit: Int, idsOf: (WebPlay) -> List<Long>): Flow<List<PlayCount>> =
        plays.map { log ->
            val timesById = mutableMapOf<Long, MutableList<Long>>()
            log.forEach { play ->
                idsOf(play).forEach { id -> timesById.getOrPut(id) { mutableListOf() }.add(play.timeMs) }
            }
            timesById
                .map { (id, times) -> PlayCount(id = id, playCount = times.size, lastPlayedMs = times.max()) }
                .filter { it.playCount > 1 }
                .sortedWith(compareByDescending<PlayCount> { it.playCount }.thenByDescending { it.lastPlayedMs })
                .take(limit)
        }

    private fun load(): List<WebPlay> = try {
        window.localStorage.getItem(KEY)?.let { json.decodeFromString<List<WebPlay>>(it) } ?: emptyList()
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        // A corrupt / schema-changed blob shouldn't brick the app — start fresh and overwrite on
        // the next recordPlay.
        hatchet.w("Failed to read web playback history, starting empty: $t")
        emptyList()
    }

    private fun persist(log: List<WebPlay>) {
        window.localStorage.setItem(KEY, json.encodeToString(log))
    }

    private companion object {
        private const val KEY = "chipbox.playback_history"
    }
}
