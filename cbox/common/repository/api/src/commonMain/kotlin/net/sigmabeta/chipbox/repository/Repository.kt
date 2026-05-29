package net.sigmabeta.chipbox.repository

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track

interface Repository {
    // Lists
    fun getAllArtists(
        withTracks: Boolean = false,
        withGames: Boolean = false
    ): Flow<Data<List<Artist>>>

    fun getAllGames(
        withTracks: Boolean = false,
        withArtists: Boolean = false
    ): Flow<Data<List<Game>>>

    fun getAllTracks(
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): Flow<Data<List<Track>>>

    suspend fun getTracksForGame(
        id: Long,
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): List<Track>

    suspend fun getTracksForArtist(
        id: Long,
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): List<Track>

    suspend fun getTracksForPlatform(
        platform: Platform,
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): List<Track>

    fun getGamesForPlatform(platform: Platform): Flow<Data<List<Game>>>

    fun getAvailablePlatforms(): Flow<Data<List<Platform>>>

    // Individual models
    fun getGame(
        id: Long,
        withTracks: Boolean = false,
        withArtists: Boolean = false
    ): Flow<Data<Game?>>

    fun getArtist(
        id: Long,
        withTracks: Boolean = false,
        withGames: Boolean = false
    ): Flow<Data<Artist?>>

    /**
     * Pre-scan snapshot of every stored game's folder signature and track count, keyed by
     * [RawGame.folderKey]. The scanner loads this once and uses it to skip re-reading folders whose
     * recomputed signature matches.
     */
    suspend fun folderSnapshots(): Map<String, FolderSnapshot>

    /**
     * Idempotently reconcile one scanned game (identified by [RawGame.folderKey]) into the library:
     * insert it if new, otherwise update its metadata and reconcile its tracks by (path,
     * trackNumber) — adding new songs, updating changed ones, deleting songs no longer present —
     * and refresh its artist links. Safe to call concurrently for distinct folders.
     *
     * Returns the game's id plus what actually happened, so the scanner can raise the right
     * `ScannerEvent` (and carry the id for navigation): [GameWriteResult.ADDED] for a new game,
     * [GameWriteResult.UPDATED] when an existing game meaningfully changed, [GameWriteResult.UNCHANGED]
     * when re-scanning produced identical data.
     */
    suspend fun upsertGame(rawGame: RawGame): GameWriteOutcome

    /**
     * Sweep step of an idempotent scan: delete every game whose folder was not seen this scan
     * (cascading its tracks and joins), then drop any artists left with no tracks. [keptFolderKeys]
     * is the set of [RawGame.folderKey]s that produced a game during the scan.
     *
     * Returns the titles of the games that were removed, so the scanner can raise a
     * `ScannerEvent.GameRemoved` for each.
     */
    suspend fun pruneGames(keptFolderKeys: Set<String>): List<String>

    suspend fun getTrack(
        id: Long,
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): Track?

    /**
     * One-shot uniform-random pick across the catalog. Returns null only when the catalog is
     * empty — the random "shuffle me one thing" UX (Home RNG row, etc.) calls these every tap.
     * Bytes-served Repositories (RemoteRepository over HTTP) must do the random pick
     * server-side; the alternative (fetch everything, randomOrNull locally) would round-trip
     * the whole catalog on every click.
     */
    suspend fun getRandomTrack(): Track?

    suspend fun getRandomGame(): Game?

    suspend fun getRandomArtist(): Artist?

    suspend fun clearLibrary()

    // Search
    fun searchGames(query: String): Flow<Data<List<Game>>>

    fun searchSongs(query: String): Flow<Data<List<Track>>>

    fun searchArtists(query: String): Flow<Data<List<Artist>>>

    // Search history
    fun getSearchHistory(): Flow<Data<List<SearchHistory>>>

    suspend fun addSearchHistory(query: String)

    suspend fun removeSearchHistory(id: Long)
}
