package net.sigmabeta.chipbox.repository.database

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.SearchHistoryEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.entities.joins.GameArtistJoin
import net.sigmabeta.chipbox.entities.joins.TrackArtistJoin
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.models.decodeChainFiles
import net.sigmabeta.chipbox.models.encodeChainFiles
import net.sigmabeta.chipbox.perf.traceAsync
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.FolderSnapshot
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet

/**
 * Room KMP makes every non-Flow DAO method `suspend` on non-Android targets, so every method
 * that called a `*Sync` DAO query (including the `toArtist`/`toGame`/`toTrack` model converters
 * that fan out for `withTracks`/`withGames`/`withArtists` joins) is now `suspend` too. The
 * standard-library `Iterable.map` can't accept a suspending transform, so [suspendMap] does
 * the obvious `for`-loop equivalent.
 */
class DatabaseRepository(
    database: ChipboxDatabase,
    private val hatchet: Hatchet,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Repository {
    private val artistDao = database.artistDao()
    private val gameDao = database.gameDao()
    private val trackDao = database.trackDao()

    private val gameArtistDao = database.gameArtistDao()
    private val trackArtistDao = database.trackArtistDao()

    private val searchHistoryDao = database.searchHistoryDao()

    // upsertGame runs on the scanner's IO coroutine and its DAO calls suspend (Room KMP makes them
    // suspend off-Android), so begin/end can resume on different threads — trace with `traceAsync`,
    // which needs a cookie unique among concurrently-open same-named sections.
    private val traceCookies = AtomicInteger(0)

    private fun nextCookie() = traceCookies.incrementAndGet()

    // Serializes the get-or-create-artist step across concurrent upsertGame calls. The scanner
    // processes folders in parallel; the unique index on artist.name is the backstop, and this
    // mutex keeps the lookup-then-insert from racing (and failing that constraint) in the first
    // place.
    private val artistWriteMutex = Mutex()

    override fun getAllArtists(
        withTracks: Boolean,
        withGames: Boolean
    ): Flow<Data<List<Artist>>> = setupFlow(
        { artistDao.getAll() },
        { list -> list.suspendMap { it.toArtist(withTracks, withGames) } }
    )

    override fun getAllGames(withTracks: Boolean, withArtists: Boolean) = setupFlow(
        { gameDao.getAll() },
        { list -> list.suspendMap { it.toGame(withTracks, withArtists) } }
    )

    override fun getAllTracks(withGame: Boolean, withArtists: Boolean) = setupFlow(
        { trackDao.getAll() },
        { list -> list.suspendMap { it.toTrack(withGame, withArtists) } }
    )

    override suspend fun getTracksForGame(
        id: Long,
        withGame: Boolean,
        withArtists: Boolean
    ): List<Track> = trackDao
        .getTracksForGameSync(id)
        .suspendMap { entity -> entity.toTrack(withGame, withArtists) }

    override suspend fun getTracksForArtist(
        id: Long,
        withGame: Boolean,
        withArtists: Boolean
    ): List<Track> = trackArtistDao
        .getTracksForArtistSync(id)
        .suspendMap { entity -> entity.toTrack(withGame, withArtists) }
        .sortedBy { it.game?.title }

    override suspend fun getTracksForPlatform(
        platform: Platform,
        withGame: Boolean,
        withArtists: Boolean
    ): List<Track> = trackDao
        .getTracksForPlatformSync(platform.name)
        .suspendMap { entity -> entity.toTrack(withGame, withArtists) }

    override fun getGamesForPlatform(platform: Platform) = setupFlow(
        { gameDao.getGamesForPlatform(platform.name) },
        { list -> list.suspendMap { it.toGame() } }
    )

    override fun getAvailablePlatforms() = setupFlow(
        { trackDao.getDistinctPlatforms() },
        { list -> list.map { Platform.valueOf(it) } }
    )

    override fun getGame(id: Long, withTracks: Boolean, withArtists: Boolean) = setupFlowWithId(
        id,
        { gameDao.getGame(id) },
        { it?.toGame(withTracks, withArtists) }
    )

    override fun getArtist(
        id: Long,
        withTracks: Boolean,
        withGames: Boolean
    ): Flow<Data<Artist?>> = setupFlowWithId(
        id,
        { artistDao.getArtist(id) },
        { it?.toArtist(withTracks, withGames) }
    )

    override suspend fun getTrack(
        id: Long,
        withGame: Boolean,
        withArtists: Boolean
    ): Track? = trackDao
        .getTrackSync(id)
        ?.toTrack(withGame, withArtists)

    override suspend fun folderSnapshots(): Map<String, FolderSnapshot> = gameDao
        .getSignatureRows()
        .associate { it.folderKey to FolderSnapshot(it.signature, it.trackCount) }

    override suspend fun upsertGame(rawGame: RawGame) {
        when (val existing = gameDao.getByFolderKeySync(rawGame.folderKey)) {
            null -> insertNewGame(rawGame)
            else -> updateExistingGame(existing, rawGame)
        }
    }

    private suspend fun insertNewGame(rawGame: RawGame) {
        val gameId = traceAsync(TRACE_INSERT_GAME, nextCookie()) {
            gameDao.insert(
                GameEntity(
                    title = rawGame.title,
                    photoUrl = rawGame.photoUrl,
                    folderKey = rawGame.folderKey,
                    folderSignature = rawGame.folderSignature,
                )
            )
        }
        val artistsByName = resolveGameArtists(rawGame)

        // One batched insert for all the game's tracks (a single transaction / commit) instead of a
        // row-at-a-time insert; the returned ids line up with rawGame.tracks by index.
        val trackIds = traceAsync(TRACE_INSERT_TRACKS, nextCookie()) {
            trackDao.insertAll(rawGame.tracks.map { it.toTrackEntity(gameId) })
        }
        val idByTrackKey = rawGame.tracks.zip(trackIds).associate { (track, id) -> track.trackKey() to id }
        linkArtists(gameId, rawGame, idByTrackKey, artistsByName)
    }

    private suspend fun updateExistingGame(existing: GameEntity, rawGame: RawGame) {
        val gameId = existing.id
        // We only reach the update path because the folder's signature changed, so refresh the row
        // (title/photo may have changed) and store the new signature for next time.
        gameDao.update(
            existing.copy(
                title = rawGame.title,
                photoUrl = rawGame.photoUrl,
                folderSignature = rawGame.folderSignature,
            )
        )

        val artistsByName = resolveGameArtists(rawGame)

        // Reconcile tracks by (path, trackNumber): keep+update existing rows (preserving their ids
        // so queue/now-playing references survive), insert new ones, delete the ones gone from disk.
        val existingByKey = trackDao.getTracksForGameSync(gameId).associateBy { it.path to it.trackNumber }
        val scannedKeys = rawGame.tracks.mapTo(HashSet()) { it.trackKey() }

        val removedIds = existingByKey.values.filterNot { it.trackKey() in scannedKeys }.map { it.id }
        if (removedIds.isNotEmpty()) {
            traceAsync(TRACE_DELETE_TRACKS, nextCookie()) { trackDao.deleteByIds(removedIds) }
        }

        val idByTrackKey = HashMap<Pair<String, Int>, Long>(rawGame.tracks.size)
        val toUpdate = mutableListOf<TrackEntity>()
        val toInsert = mutableListOf<RawTrack>()
        for (raw in rawGame.tracks) {
            val current = existingByKey[raw.trackKey()]
            if (current == null) {
                toInsert += raw
            } else {
                val updated = raw.toTrackEntity(gameId).copy(id = current.id)
                if (updated != current) toUpdate += updated
                idByTrackKey[raw.trackKey()] = current.id
            }
        }
        if (toUpdate.isNotEmpty()) {
            traceAsync(TRACE_INSERT_TRACKS, nextCookie()) { trackDao.updateAll(toUpdate) }
        }
        if (toInsert.isNotEmpty()) {
            val ids = traceAsync(TRACE_INSERT_TRACKS, nextCookie()) {
                trackDao.insertAll(toInsert.map { it.toTrackEntity(gameId) })
            }
            toInsert.forEachIndexed { index, raw -> idByTrackKey[raw.trackKey()] = ids[index] }
        }

        // Rebuild this game's artist links from scratch (joins are tiny, so a diff isn't worth it).
        trackArtistDao.deleteForTracks(idByTrackKey.values.toList())
        gameArtistDao.deleteForGame(gameId)
        linkArtists(gameId, rawGame, idByTrackKey, artistsByName)
    }

    // Resolve every distinct artist name across the game's tracks once, instead of a DB lookup per
    // track. Serialized across concurrent upsertGame calls (the scanner walks folders in parallel)
    // so two games can't insert the same new artist twice.
    private suspend fun resolveGameArtists(rawGame: RawGame): Map<String, ArtistEntity> =
        traceAsync(TRACE_RESOLVE_ARTISTS, nextCookie()) {
            artistWriteMutex.withLock { resolveArtists(rawGame.tracks) }
        }

    private suspend fun linkArtists(
        gameId: Long,
        rawGame: RawGame,
        idByTrackKey: Map<Pair<String, Int>, Long>,
        artistsByName: Map<String, ArtistEntity>,
    ) {
        val trackArtistJoins = rawGame.tracks.flatMap { track ->
            val trackId = idByTrackKey.getValue(track.trackKey())
            track.artistNames().map { name -> TrackArtistJoin(trackId, artistsByName.getValue(name).id) }
        }.distinct()
        traceAsync(TRACE_LINK_ARTISTS, nextCookie()) {
            trackArtistDao.insertAll(trackArtistJoins)
        }

        val gameArtistJoins = artistsByName.values
            .distinctBy { it.id }
            .map { artist -> GameArtistJoin(gameId, artist.id) }
        traceAsync(TRACE_INSERT_GAME_ARTISTS, nextCookie()) {
            gameArtistDao.insertAll(gameArtistJoins)
        }
    }

    override suspend fun pruneGames(keptFolderKeys: Set<String>) {
        val removableIds = gameDao.getAllSync()
            .filterNot { it.folderKey in keptFolderKeys }
            .map { it.id }
        if (removableIds.isNotEmpty()) {
            traceAsync(TRACE_PRUNE_GAMES, nextCookie()) { gameDao.deleteByIds(removableIds) }
        }
        traceAsync(TRACE_PRUNE_ARTISTS, nextCookie()) { artistDao.deleteOrphans() }
    }

    private suspend fun ArtistEntity.toArtist(
        withTracks: Boolean = false,
        withGames: Boolean = false
    ) = Artist(
        id,
        name,
        photoUrl,
        if (withTracks) getTracksForArtist(id, withGame = true) else null,
        if (withGames) getGamesForArtist(id) else null
    )

    private suspend fun GameEntity.toGame(
        withTracks: Boolean = false,
        withArtists: Boolean = false
    ) = Game(
        id,
        title,
        photoUrl,
        if (withArtists) getArtistsForGame(id) else null,
        if (withTracks) getTracksForGame(id) else null
    )

    private suspend fun TrackEntity.toTrack(
        withGame: Boolean = false,
        withArtists: Boolean = false
    ) = Track(
        id,
        path,
        source,
        title,
        trackLengthMs,
        trackNumber,
        fadeLengthMs,
        if (withGame) getGameById(gameId) else null,
        if (withArtists) getArtistsForTrack(id) else null,
        decodeChainFiles(chainFiles),
        extension,
        Platform.valueOf(platform),
    )

    /**
     * Look up (or create) each distinct artist name across [tracks], returning a name→entity map.
     * One [getArtistByNameSync][ArtistDao.getArtistByNameSync] per distinct name (not per track),
     * and a single batched insert for the names not already in the DB. Caller holds
     * [artistWriteMutex] so the lookup-then-insert can't race a concurrent game.
     */
    private suspend fun resolveArtists(tracks: List<RawTrack>): Map<String, ArtistEntity> {
        val names = tracks.flatMap { it.artistNames() }.distinct()
        val resolved = HashMap<String, ArtistEntity>(names.size)
        val missing = mutableListOf<String>()
        for (name in names) {
            val existing = artistDao.getArtistByNameSync(name)
            if (existing != null) resolved[name] = existing else missing += name
        }
        if (missing.isNotEmpty()) {
            val ids = artistDao.insertAll(missing.map { ArtistEntity(it, null) })
            missing.forEachIndexed { index, name ->
                resolved[name] = ArtistEntity(name, null, ids[index])
            }
        }
        return resolved
    }

    private fun RawTrack.artistNames(): List<String> = artist
        .split(DELIMITERS_ARTISTS)
        .map { it.trim() }

    // A track's reconciliation identity: a single file can yield several tracks (NSF/GBS subtracks)
    // that share a path but differ by trackNumber.
    private fun RawTrack.trackKey(): Pair<String, Int> = path to trackNumber

    private fun TrackEntity.trackKey(): Pair<String, Int> = path to trackNumber

    private fun RawTrack.toTrackEntity(gameId: Long): TrackEntity = TrackEntity(
        title,
        path,
        source,
        length,
        trackNumber,
        fadeLengthMs,
        gameId,
        encodeChainFiles(chainFiles),
        extension,
        platform.name,
    )

    private suspend fun getGameById(id: Long): Game = gameDao
        .getGameSync(id)
        .toGame()

    private suspend fun getGamesForArtist(id: Long): List<Game> = gameArtistDao
        .getGamesForArtistSync(id)
        .suspendMap { it.toGame() }

    private suspend fun getArtistsForTrack(id: Long): List<Artist> = trackArtistDao
        .getArtistsForTrackSync(id)
        .suspendMap { it.toArtist() }

    private suspend fun getArtistsForGame(id: Long): List<Artist> = gameArtistDao
        .getArtistsForGameSync(id)
        .suspendMap { it.toArtist() }

    private suspend fun getTracksForGame(id: Long): List<Track> = trackDao
        .getTracksForGameSync(id)
        .suspendMap { it.toTrack(withArtists = true) }

    override suspend fun clearLibrary() = withContext(dispatcher) {
        artistDao.nukeTable()
        gameDao.nukeTable()
        trackDao.nukeTable()
        gameArtistDao.nukeTable()
        trackArtistDao.nukeTable()
    }

    override fun searchGames(query: String) = setupFlow(
        { gameDao.searchGamesByTitle("%$query%") },
        { list -> list.suspendMap { it.toGame() } }
    )

    override fun searchSongs(query: String) = setupFlow(
        { trackDao.searchTracksByTitle("%$query%") },
        { list -> list.suspendMap { it.toTrack(withGame = true) } }
    )

    override fun searchArtists(query: String) = setupFlow(
        { artistDao.searchArtistsByName("%$query%") },
        { list -> list.suspendMap { it.toArtist() } }
    )

    override fun getSearchHistory(): Flow<Data<List<SearchHistory>>> = setupFlow(
        { searchHistoryDao.getRecent() },
        { list -> list.map { it.toSearchHistory() } }
    )

    override suspend fun addSearchHistory(query: String): Unit = withContext(dispatcher) {
        if (searchHistoryDao.getByQuerySync(query) == null) {
            searchHistoryDao.insert(SearchHistoryEntity(query, currentTimeMillis()))
        }
    }

    override suspend fun removeSearchHistory(id: Long) = withContext(dispatcher) {
        searchHistoryDao.deleteById(id)
    }

    private fun SearchHistoryEntity.toSearchHistory() = SearchHistory(id, query)

    private fun <Entity, Model> setupFlow(
        databaseOp: () -> Flow<Entity>,
        converter: suspend (Entity) -> Model
    ): Flow<Data<Model>> = databaseOp()
        .map { converter(it) }
        .map<Model, Data<Model>> { model ->
            if (model is List<*>) {
                if (model.isNotEmpty()) Data.Succeeded(model) else Data.Empty
            } else {
                if (model != null) Data.Succeeded(model) else Data.Empty
            }
        }
        .onStart { emit(Data.Loading) }
        .catch {
            hatchet.e("Error: ${it.message}")
            emit(Data.Failed(it.message ?: ERR_UNKNOWN))
        }
        .flowOn(dispatcher)

    private fun <Entity, Model> setupFlowWithId(
        id: Long,
        databaseOp: (Long) -> Flow<Entity>,
        converter: suspend (Entity) -> Model
    ): Flow<Data<Model>> = databaseOp(id)
        .map { converter(it) }
        .map<Model, Data<Model>> { model ->
            if (model is List<*>) {
                if (model.isNotEmpty()) Data.Succeeded(model) else Data.Empty
            } else {
                if (model != null) Data.Succeeded(model) else Data.Empty
            }
        }
        .onStart { emit(Data.Loading) }
        .catch {
            hatchet.e("Error: ${it.message}")
            emit(Data.Failed(it.message ?: ERR_UNKNOWN))
        }
        .flowOn(dispatcher)

    companion object {
        const val ERR_UNKNOWN = "Unknown Error"
        val DELIMITERS_ARTISTS = Regex(", &|,| or | and |&")

        // Perfetto trace labels for the scan-time insert path. All `traceAsync` because the DAO
        // calls suspend; the per-track spans (resolve/insert/link) fire once per track, so a slow
        // scan shows here as a wall of insert spans dominated by whichever sub-step is the cost.
        private const val TRACE_INSERT_GAME = "Repository:insertGame"
        private const val TRACE_INSERT_TRACKS = "Repository:insertTracks"
        private const val TRACE_INSERT_GAME_ARTISTS = "Repository:insertGameArtists"
        private const val TRACE_RESOLVE_ARTISTS = "Repository:resolveArtists"
        private const val TRACE_LINK_ARTISTS = "Repository:linkTrackArtists"
        private const val TRACE_DELETE_TRACKS = "Repository:deleteTracks"
        private const val TRACE_PRUNE_GAMES = "Repository:pruneGames"
        private const val TRACE_PRUNE_ARTISTS = "Repository:pruneArtists"
    }
}

/**
 * `Iterable.map` accepts only non-suspend transforms, so this is the obvious for-loop
 * equivalent for the (sequential) suspending case the DAO `*Sync` calls introduce.
 */
private suspend fun <T, R> Iterable<T>.suspendMap(transform: suspend (T) -> R): List<R> {
    val out = mutableListOf<R>()
    for (e in this) out += transform(e)
    return out
}

// kotlin.system.currentTimeMillis() works only on JVM; for KMP both targets get the same via
// kotlin.time.Clock + nowMilliseconds(). Keep the small platform-neutral helper here.
private fun currentTimeMillis(): Long = System.currentTimeMillis()
