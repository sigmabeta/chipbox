package net.sigmabeta.chipbox.browserstate

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.history.PlayCount
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.history.RecentPlay
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.sage.storage.common.Storage

/**
 * [PlaybackHistoryRepository] over browser `localStorage` (through [VersionedStorageStore]).
 *
 * The log is versioned, bounded ([PlayHistoryState.MAX_ENTRIES]), and cross-tab-live: every read
 * stream is derived from the storage flow, so another tab's play appears without a reload. The
 * unversioned JSON array the web app wrote before this store existed is read as schema version 0;
 * the next recorded play persists it at the current version.
 *
 * Reads are the same folds the Room repository's SQL performs: recently-played is distinct tracks
 * newest first, most-played is counts above one ordered by count then recency, and a multi-artist
 * track credits each artist. Ids are persisted only — the Home modules hydrate them against the
 * library and drop unknown ids, so an RSN re-key or a rebuilt library never errors.
 */
@OptIn(ExperimentalTime::class)
class StoragePlaybackHistoryRepository(
    private val store: VersionedStorageStore<PlayHistoryState>,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : PlaybackHistoryRepository {

    constructor(
        storage: Storage,
        events: BrowserStateEvents,
        now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ) : this(
        VersionedStorageStore(
            storage = storage,
            key = BrowserStateKeys.PLAYBACK_HISTORY,
            codec = BrowserStateCodecs.playHistory,
            emptyValue = PlayHistoryState(),
            events = events,
        ),
        now,
    )

    override suspend fun recordPlay(track: Track) {
        store.update { state ->
            state.withPlay(
                PlayHistoryEntry(
                    trackId = track.id,
                    // A real Track always carries its game id; 0 means "unset".
                    gameId = track.gameId,
                    artistIds = track.artists?.map { it.id } ?: emptyList(),
                    timeMs = now(),
                ),
            )
        }
    }

    override fun recentlyPlayed(limit: Int): Flow<List<RecentPlay>> = store.values().map { state ->
        state.bounded().plays
            .groupBy { it.trackId }
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

    override fun playedGameIds(): Flow<List<Long>> = store.values().map { state ->
        state.bounded().plays.mapNotNull { it.gameId.takeIf { id -> id != 0L } }.distinct()
    }

    override suspend fun clearHistory() {
        store.update { PlayHistoryState() }
    }

    /**
     * Shared most-played fold. [idsOf] projects each play to the ids it increments — the track
     * itself, its game, or each of its artists — so one play of a multi-artist track bumps every
     * credited artist, exactly as `RealPlaybackHistoryRepository.recordPlay` does. Mirrors the SQL
     * `WHERE playCount > 1 ORDER BY playCount DESC, lastPlayedMs DESC` the Room DAOs apply.
     */
    private fun mostPlayed(limit: Int, idsOf: (PlayHistoryEntry) -> List<Long>): Flow<List<PlayCount>> =
        store.values().map { state ->
            val timesById = mutableMapOf<Long, MutableList<Long>>()
            state.bounded().plays.forEach { play ->
                idsOf(play).forEach { id -> timesById.getOrPut(id) { mutableListOf() }.add(play.timeMs) }
            }
            timesById
                .map { (id, times) -> PlayCount(id = id, playCount = times.size, lastPlayedMs = times.max()) }
                .filter { it.playCount > 1 }
                .sortedWith(compareByDescending<PlayCount> { it.playCount }.thenByDescending { it.lastPlayedMs })
                .take(limit)
        }
}
