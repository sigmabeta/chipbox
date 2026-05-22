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

    // addGame runs on the scanner's IO coroutine and its DAO calls suspend (Room KMP makes them
    // suspend off-Android), so begin/end can resume on different threads — trace with `traceAsync`,
    // which needs a cookie unique among concurrently-open same-named sections.
    private val traceCookies = AtomicInteger(0)

    private fun nextCookie() = traceCookies.incrementAndGet()

    // Serializes the get-or-create-artist step across concurrent addGame calls. The scanner
    // processes folders in parallel, and there's no unique index on artist.name, so two games
    // resolving the same new artist at once would otherwise insert it twice.
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

    override suspend fun addGame(rawGame: RawGame) {
        val gameId = traceAsync(TRACE_INSERT_GAME, nextCookie()) {
            gameDao.insert(GameEntity(rawGame.title, rawGame.photoUrl))
        }

        // Resolve every distinct artist name across the game's tracks once, instead of a DB
        // lookup per track. Serialized across concurrent addGame calls (the scanner walks
        // folders in parallel) so two games can't insert the same new artist twice — there's
        // no unique index on artist.name to lean on.
        val artistsByName = traceAsync(TRACE_RESOLVE_ARTISTS, nextCookie()) {
            artistWriteMutex.withLock { resolveArtists(rawGame.tracks) }
        }

        // One batched insert for all the game's tracks (a single transaction / commit) instead
        // of a row-at-a-time insert; the returned ids line up with rawGame.tracks by index.
        val trackIds = traceAsync(TRACE_INSERT_TRACKS, nextCookie()) {
            trackDao.insertAll(rawGame.tracks.map { it.toTrackEntity(gameId) })
        }

        val trackArtistJoins = rawGame.tracks.zip(trackIds).flatMap { (track, trackId) ->
            track.artistNames().map { name -> TrackArtistJoin(trackId, artistsByName.getValue(name).id) }
        }
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
