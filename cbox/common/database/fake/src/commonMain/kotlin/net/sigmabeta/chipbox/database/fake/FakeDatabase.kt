package net.sigmabeta.chipbox.database.fake

import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.database.dao.ArtistDao
import net.sigmabeta.chipbox.database.dao.GameArtistDao
import net.sigmabeta.chipbox.database.dao.GameDao
import net.sigmabeta.chipbox.database.dao.GameSignatureRow
import net.sigmabeta.chipbox.database.dao.SearchHistoryDao
import net.sigmabeta.chipbox.database.dao.TrackArtistDao
import net.sigmabeta.chipbox.database.dao.TrackDao
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.SearchHistoryEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.entities.joins.GameArtistJoin
import net.sigmabeta.chipbox.entities.joins.TrackArtistJoin

/**
 * In-memory backing for the 6 DAO fakes the production Room runtime in `:database:real`
 * implements. One bag of maps + a shared "data changed" trigger — every write bumps the
 * trigger, and Flow-returning DAO methods derive from it so subscribers re-emit, the same way
 * production Room re-emits when an underlying table changes.
 *
 * Not threadsafe — callers run on a single coroutine (typically via
 * [kotlinx.coroutines.test.runTest] + `UnconfinedTestDispatcher`). That keeps the fakes simple
 * and the assertions deterministic.
 *
 * No attempt to enforce SQL constraints (unique indexes, etc.) — the production code never
 * relies on the DB rejecting bad input; it serialises writes through `artistWriteMutex` and
 * does its own lookup-before-insert. FK cascades on delete *are* mimicked because production
 * code relies on them ([gameDao.deleteByIds] cascades to tracks and track_artist_join so
 * `artistDao.deleteOrphans()` correctly sees zero references).
 *
 * The `getRandom()` DAO methods draw from [random], which defaults to a fixed-seed generator so
 * tests stay deterministic (mirroring the rest of this fake); pass a different [Random] when a
 * test needs to control or vary the draw.
 */
class FakeDatabase(private val random: Random = Random(0)) {

    val artists: MutableMap<Long, ArtistEntity> = mutableMapOf()
    val games: MutableMap<Long, GameEntity> = mutableMapOf()
    val tracks: MutableMap<Long, TrackEntity> = mutableMapOf()
    val gameArtistJoins: MutableList<GameArtistJoin> = mutableListOf()
    val trackArtistJoins: MutableList<TrackArtistJoin> = mutableListOf()
    val searchHistory: MutableMap<Long, SearchHistoryEntity> = mutableMapOf()

    private var idCounter: Long = 0L
    private fun nextId(): Long = ++idCounter

    // Bumped on every write. Flow-returning DAO methods .map over this so the next emission
    // recomputes from current table state — mirrors Room's "Flow re-emits on table change".
    private val trigger = MutableStateFlow(0L)
    private fun bump() {
        trigger.value = trigger.value + 1L
    }

    private fun <T> observe(compute: () -> T): Flow<T> = trigger.map { compute() }

    val artistDao: ArtistDao = object : ArtistDao {
        override fun getArtist(artistId: Long): Flow<ArtistEntity?> = observe { artists[artistId] }
        override suspend fun getArtistByNameSync(name: String): ArtistEntity? =
            artists.values.firstOrNull { it.name == name }
        override suspend fun getArtistByIdSync(artistId: Long): ArtistEntity = artists.getValue(artistId)
        override suspend fun getRandom(): ArtistEntity? = artists.values.randomOrNull(random)
        override fun getAll(): Flow<List<ArtistEntity>> = observe {
            artists.values.sortedBy { it.name.lowercase() }
        }
        override fun searchArtistsByName(name: String): Flow<List<ArtistEntity>> = observe {
            artists.values.filter { sqlLike(name, it.name) }.sortedBy { it.name.lowercase() }
        }
        override suspend fun insert(artist: ArtistEntity): Long {
            val id = nextId()
            artists[id] = artist.copy(id = id)
            bump()
            return id
        }
        override suspend fun insertAll(artists: List<ArtistEntity>): List<Long> =
            artists.map { insert(it) }
        override suspend fun deleteOrphans() {
            val referenced = trackArtistJoins.mapTo(mutableSetOf()) { it.artistId }
            artists.keys.toList().forEach { if (it !in referenced) artists.remove(it) }
            bump()
        }
        override suspend fun nukeTable() {
            artists.clear()
            bump()
        }
    }

    val gameDao: GameDao = object : GameDao {
        override fun getGame(gameId: Long): Flow<GameEntity?> = observe { games[gameId] }
        override suspend fun getByFolderKeySync(folderKey: String): GameEntity? =
            games.values.firstOrNull { it.folderKey == folderKey }
        override suspend fun getRandom(): GameEntity? = games.values.randomOrNull(random)
        override suspend fun getAllSync(): List<GameEntity> = games.values.toList()
        override suspend fun getSignatureRows(): List<GameSignatureRow> = games.values.map { g ->
            GameSignatureRow(
                folderKey = g.folderKey,
                signature = g.folderSignature,
                trackCount = tracks.values.count { it.gameId == g.id },
            )
        }
        override suspend fun update(game: GameEntity) {
            games[game.id] = game
            bump()
        }
        override suspend fun deleteByIds(ids: List<Long>) {
            val idSet = ids.toSet()
            // Manual cascade matching the FK chain: game → track → track_artist_join, plus
            // game_artist_join. Two-level cascade is required so the subsequent
            // `artistDao.deleteOrphans()` correctly sees zero references when the deleted game
            // was the only thing referring to its artists.
            val cascadedTrackIds = tracks.values.filter { it.gameId in idSet }.map { it.id }.toSet()
            trackArtistJoins.removeAll { it.trackId in cascadedTrackIds }
            tracks.keys.toList().forEach { tid -> if (tracks[tid]?.gameId in idSet) tracks.remove(tid) }
            gameArtistJoins.removeAll { it.gameId in idSet }
            ids.forEach { games.remove(it) }
            bump()
        }
        override suspend fun getGameSync(gameId: Long): GameEntity = games.getValue(gameId)
        override fun getAll(): Flow<List<GameEntity>> = observe {
            games.values.sortedBy { it.title.lowercase() }
        }
        override fun getGamesForPlatform(platformName: String): Flow<List<GameEntity>> = observe {
            val gameIds = tracks.values.filter { it.platform == platformName }.map { it.gameId }.toSet()
            games.values.filter { it.id in gameIds }.sortedBy { it.title.lowercase() }
        }
        override fun searchGamesByTitle(title: String): Flow<List<GameEntity>> = observe {
            games.values.filter { sqlLike(title, it.title) }.sortedBy { it.title.lowercase() }
        }
        override suspend fun insert(game: GameEntity): Long {
            val id = nextId()
            games[id] = game.copy(id = id)
            bump()
            return id
        }
        override suspend fun nukeTable() {
            games.clear()
            bump()
        }
    }

    val trackDao: TrackDao = object : TrackDao {
        override fun getAll(): Flow<List<TrackEntity>> = observe {
            tracks.values.sortedBy { it.title }
        }
        override fun getTracksForGame(gameId: Long): Flow<List<TrackEntity>> = observe {
            tracks.values.filter { it.gameId == gameId }
        }
        override suspend fun getTracksForGameSync(gameId: Long): List<TrackEntity> =
            tracks.values.filter { it.gameId == gameId }
        override suspend fun getTracksForPlatformSync(platformName: String): List<TrackEntity> =
            tracks.values.filter { it.platform == platformName }
        override fun getDistinctPlatforms(): Flow<List<String>> = observe {
            tracks.values.map { it.platform }.distinct()
        }
        override fun getTrack(trackId: Long): Flow<TrackEntity> = observe { tracks.getValue(trackId) }
        override suspend fun getTrackSync(trackId: Long): TrackEntity? = tracks[trackId]
        override suspend fun getRandom(): TrackEntity? = tracks.values.randomOrNull(random)
        override fun searchTracksByTitle(title: String): Flow<List<TrackEntity>> = observe {
            tracks.values.filter { sqlLike(title, it.title) }.sortedBy { it.title.lowercase() }
        }
        override suspend fun insert(track: TrackEntity): Long {
            val id = nextId()
            tracks[id] = track.copy(id = id)
            bump()
            return id
        }
        override suspend fun insertAll(tracks: List<TrackEntity>): List<Long> = tracks.map { insert(it) }
        override suspend fun updateAll(tracks: List<TrackEntity>) {
            tracks.forEach { this@FakeDatabase.tracks[it.id] = it }
            bump()
        }
        override suspend fun deleteByIds(ids: List<Long>) {
            val idSet = ids.toSet()
            trackArtistJoins.removeAll { it.trackId in idSet }
            ids.forEach { tracks.remove(it) }
            bump()
        }
        override suspend fun nukeTable() {
            tracks.clear()
            bump()
        }
    }

    val gameArtistDao: GameArtistDao = object : GameArtistDao {
        override suspend fun insertAll(gameArtistJoins: List<GameArtistJoin>) {
            this@FakeDatabase.gameArtistJoins += gameArtistJoins
            bump()
        }
        override suspend fun deleteForGame(gameId: Long) {
            gameArtistJoins.removeAll { it.gameId == gameId }
            bump()
        }
        override suspend fun getArtistIdsForGame(gameId: Long): List<Long> =
            artistsForGameSync(gameId).map { it.id }
        override suspend fun getGameIdsForArtist(artistId: Long): List<Long> = gameArtistJoins
            .filter { it.artistId == artistId }
            .mapNotNull { games[it.gameId] }
            .sortedBy { it.title.lowercase() }
            .map { it.id }
        override fun getArtistsForGame(gameId: Long): Flow<List<ArtistEntity>> = observe {
            artistsForGameSync(gameId)
        }
        override suspend fun getArtistsForGameSync(gameId: Long): List<ArtistEntity> =
            artistsForGameSync(gameId)
        override suspend fun getGamesForArtistSync(artistId: Long): List<GameEntity> = gameArtistJoins
            .filter { it.artistId == artistId }
            .mapNotNull { games[it.gameId] }
            .sortedBy { it.title.lowercase() }
        override fun getGamesForArtist(artistId: Long): Flow<List<GameEntity>> = observe {
            gameArtistJoins
                .filter { it.artistId == artistId }
                .mapNotNull { games[it.gameId] }
                .sortedBy { it.title.lowercase() }
        }
        override suspend fun nukeTable() {
            gameArtistJoins.clear()
            bump()
        }

        private fun artistsForGameSync(gameId: Long): List<ArtistEntity> = gameArtistJoins
            .filter { it.gameId == gameId }
            .mapNotNull { artists[it.artistId] }
            .sortedBy { it.name.lowercase() }
    }

    val trackArtistDao: TrackArtistDao = object : TrackArtistDao {
        override suspend fun insertAll(trackArtistJoins: List<TrackArtistJoin>) {
            this@FakeDatabase.trackArtistJoins += trackArtistJoins
            bump()
        }
        override suspend fun deleteForTracks(trackIds: List<Long>) {
            val idSet = trackIds.toSet()
            trackArtistJoins.removeAll { it.trackId in idSet }
            bump()
        }
        override suspend fun getArtistIdsForTrack(trackId: Long): List<Long> =
            artistsForTrackSync(trackId).map { it.id }
        override suspend fun getArtistsForTrack(trackId: Long): List<ArtistEntity> =
            artistsForTrackSync(trackId)
        override suspend fun getArtistsForTrackSync(trackId: Long): List<ArtistEntity> =
            artistsForTrackSync(trackId)

        // Mirror getTracksForArtistSync's SQL order: A–Z by game title, then track number.
        override suspend fun getTracksForArtistSync(artistId: Long): List<TrackEntity> = trackArtistJoins
            .filter { it.artistId == artistId }
            .mapNotNull { tracks[it.trackId] }
            .sortedWith(compareBy({ games[it.gameId]?.title?.lowercase() }, { it.trackNumber }))
        override fun getTracksForArtist(artistId: Long): Flow<List<TrackEntity>> = observe {
            trackArtistJoins
                .filter { it.artistId == artistId }
                .mapNotNull { tracks[it.trackId] }
                .sortedWith(compareBy({ games[it.gameId]?.title?.lowercase() }, { it.trackNumber }))
        }
        override suspend fun nukeTable() {
            trackArtistJoins.clear()
            bump()
        }

        private fun artistsForTrackSync(trackId: Long): List<ArtistEntity> = trackArtistJoins
            .filter { it.trackId == trackId }
            .mapNotNull { artists[it.artistId] }
            .sortedBy { it.name.lowercase() }
    }

    val searchHistoryDao: SearchHistoryDao = object : SearchHistoryDao {
        override fun getRecent(): Flow<List<SearchHistoryEntity>> = observe {
            searchHistory.values.sortedByDescending { it.timeMs }.take(SEARCH_HISTORY_LIMIT)
        }
        override suspend fun getByQuerySync(query: String): SearchHistoryEntity? =
            searchHistory.values.firstOrNull { it.query == query }
        override suspend fun insert(entry: SearchHistoryEntity): Long {
            val id = nextId()
            searchHistory[id] = entry.copy(id = id)
            bump()
            return id
        }
        override suspend fun deleteById(id: Long) {
            searchHistory.remove(id)
            bump()
        }
    }

    private companion object {
        /** Mirrors the DAO query's `LIMIT 10`. */
        const val SEARCH_HISTORY_LIMIT = 10

        /**
         * Stripped-down SQL LIKE — production DAOs wrap the search term in `%…%` and rely on
         * SQLite's LIKE matching. We support only the leading/trailing `%` wildcards and treat
         * the match as case-insensitive (the production queries `COLLATE NOCASE` their results).
         */
        fun sqlLike(pattern: String, value: String): Boolean {
            val unwrapped = pattern.removePrefix("%").removeSuffix("%")
            return value.contains(unwrapped, ignoreCase = true)
        }
    }
}
