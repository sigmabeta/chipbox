package net.sigmabeta.chipbox.repository.memory

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.FolderSnapshot
import net.sigmabeta.chipbox.repository.GameWriteOutcome
import net.sigmabeta.chipbox.repository.GameWriteResult
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.memory.models.MemoryArtist
import net.sigmabeta.chipbox.repository.memory.models.MemoryGame
import net.sigmabeta.chipbox.repository.memory.models.MemoryTrack
import net.sigmabeta.chipbox.utils.ioDispatcher

open class MemoryRepository(
    dispatcher: CoroutineDispatcher = ioDispatcher
) : Repository {
    private val repositoryScope = CoroutineScope(dispatcher)

    private var gamesByTitle = mutableMapOf<String, MemoryGame>()
    private var tracksByTitle = mutableMapOf<String, MemoryTrack>()
    private var artistsByName = mutableMapOf<String, MemoryArtist>()

    private var gamesById = mutableMapOf<Long, MemoryGame>()
    private var tracksById = mutableMapOf<Long, MemoryTrack>()
    private var artistsById = mutableMapOf<Long, MemoryArtist>()

    private var lastPrimaryKey = 0L

    private val artistsLoadEvents = MutableSharedFlow<Data<List<Artist>>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val gamesLoadEvents = MutableSharedFlow<Data<List<Game>>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val tracksLoadEvents = MutableSharedFlow<Data<List<Track>>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // TODO Garbage idea. Wrong data will load on second instance of a screen
    private var artistsLoaded = false
    private var gamesLoaded = false
    private var tracksLoaded = false

    override fun getAllArtists(
        withTracks: Boolean,
        withGames: Boolean,
        limit: Int?,
        offset: Int
    ): Flow<Data<List<Artist>>> {
        // A paged request gets a fresh one-shot flow so distinct pages don't clobber each other
        // through the shared, lazily-cached unpaged flow below.
        if (limit != null || offset != 0) {
            return flow {
                emit(Data.Loading)
                val page = artistsByName
                    .values
                    .sortedBy { it.name.lowercase() }
                    .drop(offset)
                    .let { if (limit != null) it.take(limit) else it }
                    .map { it.toArtist(withGames, withTracks) }
                emit(if (page.isNotEmpty()) Data.Succeeded(page) else Data.Empty)
            }
        }
        if (!artistsLoaded) {
            artistsLoaded = true
            repositoryScope.launch {
                artistsLoadEvents.emit(Data.Loading)

                val artists = artistsByName
                    .values
                    .sortedBy { it.name.lowercase() }
                    .map { it.toArtist(withGames, withTracks) }
                val data = if (artists.isNotEmpty()) {
                    Data.Succeeded(artists)
                } else {
                    Data.Empty
                }

                artistsLoadEvents.emit(data)
            }
        }
        return artistsLoadEvents.asSharedFlow()
    }

    override fun getAllGames(
        withTracks: Boolean,
        withArtists: Boolean,
        limit: Int?,
        offset: Int
    ): Flow<Data<List<Game>>> {
        // A paged request gets a fresh one-shot flow so distinct pages don't clobber each other
        // through the shared, lazily-cached unpaged flow below.
        if (limit != null || offset != 0) {
            return flow {
                emit(Data.Loading)
                val page = gamesByTitle
                    .values
                    .sortedBy { it.title }
                    .drop(offset)
                    .let { if (limit != null) it.take(limit) else it }
                    .map { it.toGame(withTracks, withArtists) }
                emit(if (page.isNotEmpty()) Data.Succeeded(page) else Data.Empty)
            }
        }
        if (!gamesLoaded) {
            gamesLoaded = true
            repositoryScope.launch {
                gamesLoadEvents.emit(Data.Loading)

                val games = getLatestAllGames(withTracks, withArtists)
                val data = if (games.isNotEmpty()) {
                    Data.Succeeded(games)
                } else {
                    Data.Empty
                }

                gamesLoadEvents.emit(data)
            }
        }
        return gamesLoadEvents.asSharedFlow()
    }

    override fun getAllTracks(
        withGame: Boolean,
        withArtists: Boolean,
        limit: Int?,
        offset: Int
    ): Flow<Data<List<Track>>> {
        // A paged request gets a fresh one-shot flow so distinct pages don't clobber each other
        // through the shared, lazily-cached unpaged flow below.
        if (limit != null || offset != 0) {
            return flow {
                emit(Data.Loading)
                val page = tracksByTitle
                    .values
                    .sortedBy { it.title }
                    .drop(offset)
                    .let { if (limit != null) it.take(limit) else it }
                    .map { it.toTrack(withGame, withArtists) }
                emit(if (page.isNotEmpty()) Data.Succeeded(page) else Data.Empty)
            }
        }
        if (!tracksLoaded) {
            tracksLoaded = true
            repositoryScope.launch {
                tracksLoadEvents.emit(Data.Loading)

                val tracks = tracksByTitle
                    .values
                    .sortedBy { it.title }
                    .map { it.toTrack(withGame, withArtists) }

                val data = if (tracks.isNotEmpty()) {
                    Data.Succeeded(tracks)
                } else {
                    Data.Empty
                }

                tracksLoadEvents.emit(data)
            }
        }
        return tracksLoadEvents.asSharedFlow()
    }

    override fun getTracksByIds(
        ids: List<Long>,
        withGame: Boolean,
        withArtists: Boolean
    ): Flow<Data<List<Track>>> = flow {
        emit(Data.Loading)
        val tracks = ids.mapNotNull { tracksById[it]?.toTrack(withGame, withArtists) }
        emit(if (tracks.isNotEmpty()) Data.Succeeded(tracks) else Data.Empty)
    }

    override suspend fun getTracksForGame(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> {
        TODO("Not yet implemented")
    }

    override suspend fun getTracksForArtist(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> {
        TODO("Not yet implemented")
    }

    override suspend fun getTracksForPlatform(
        platform: Platform,
        withGame: Boolean,
        withArtists: Boolean
    ): List<Track> = tracksByTitle
        .values
        .filter { it.toTrack().platform == platform }
        .sortedBy { it.title }
        .map { it.toTrack(withGame, withArtists) }

    override fun getGamesForPlatform(platform: Platform): Flow<Data<List<Game>>> = flow {
        emit(Data.Loading)
        val games = gamesByTitle
            .values
            .filter { game -> game.tracks.any { it.toTrack().platform == platform } }
            .sortedBy { it.title }
            .map { it.toGame() }
        emit(if (games.isNotEmpty()) Data.Succeeded(games) else Data.Empty)
    }

    override fun getAvailablePlatforms(): Flow<Data<List<Platform>>> = flow {
        emit(Data.Loading)
        val platforms = tracksById
            .values
            .map { it.toTrack().platform }
            .distinct()
            .sortedBy { it.ordinal }
        emit(if (platforms.isNotEmpty()) Data.Succeeded(platforms) else Data.Empty)
    }

    // A fresh cold flow per call, looking up the requested id each time — so navigating to a second
    // game/artist loads THAT one, not a cached first result. (The previous shared-flow-with-a-
    // load-once-flag approach replayed the first id's data for every later screen.)
    override fun getGame(
        id: Long,
        withTracks: Boolean,
        withArtists: Boolean
    ): Flow<Data<Game?>> = flow {
        emit(Data.Loading)
        val game = gamesById[id]?.toGame(withTracks, withArtists)
        emit(if (game != null) Data.Succeeded(game) else Data.Empty)
    }

    override fun getArtist(
        id: Long,
        withTracks: Boolean,
        withGames: Boolean
    ): Flow<Data<Artist?>> = flow {
        emit(Data.Loading)
        val artist = artistsById[id]?.toArtist(withGames, withTracks)
        emit(if (artist != null) Data.Succeeded(artist) else Data.Empty)
    }

    override suspend fun getTrack(
        id: Long,
        withGame: Boolean,
        withArtists: Boolean
    ) = tracksById[id]?.toTrack(withGame, withArtists)

    override suspend fun getRandomTrack(): Track? =
        tracksById.values.randomOrNull()?.toTrack(withGame = false, withArtists = false)

    override suspend fun getRandomGame(): Game? =
        gamesById.values.randomOrNull()?.toGame(withTracks = false, withArtists = false)

    override suspend fun getRandomArtist(): Artist? =
        artistsById.values.randomOrNull()?.toArtist(withTracks = false, withGames = false)

    override suspend fun clearLibrary() {
        resetData()
    }

    override fun searchGames(query: String): Flow<Data<List<Game>>> = flow {
        emit(Data.Loading)
        val matches = gamesByTitle
            .values
            .filter { it.title.contains(query, ignoreCase = true) }
            .sortedBy { it.title }
            .map { it.toGame() }
        emit(if (matches.isNotEmpty()) Data.Succeeded(matches) else Data.Empty)
    }

    override fun searchSongs(query: String): Flow<Data<List<Track>>> = flow {
        emit(Data.Loading)
        val matches = tracksByTitle
            .values
            .filter { it.title.contains(query, ignoreCase = true) }
            .sortedBy { it.title }
            .map { it.toTrack(withGame = true, withArtists = true) }
        emit(if (matches.isNotEmpty()) Data.Succeeded(matches) else Data.Empty)
    }

    override fun searchArtists(query: String): Flow<Data<List<Artist>>> = flow {
        emit(Data.Loading)
        val matches = artistsByName
            .values
            .filter { it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
            .map { it.toArtist() }
        emit(if (matches.isNotEmpty()) Data.Succeeded(matches) else Data.Empty)
    }

    private val searchHistory = MutableStateFlow<List<SearchHistory>>(emptyList())
    private var searchHistoryIdCounter = 0L

    override fun getSearchHistory(): Flow<Data<List<SearchHistory>>> = searchHistory.map {
        if (it.isEmpty()) Data.Empty else Data.Succeeded(it)
    }

    override suspend fun addSearchHistory(query: String) {
        if (searchHistory.value.none { it.query == query }) {
            searchHistory.value =
                listOf(SearchHistory(searchHistoryIdCounter++, query)) + searchHistory.value
        }
    }

    override suspend fun removeSearchHistory(id: Long) {
        searchHistory.value = searchHistory.value.filterNot { it.id == id }
    }

    // In-memory fake: a plain add is enough for previews/tests; idempotent reconciliation and
    // skip-unchanged-folder logic live in the real repository.
    override suspend fun folderSnapshots(): Map<String, FolderSnapshot> = emptyMap()

    override suspend fun pruneGames(keptFolderKeys: Set<String>): List<String> = emptyList()

    override suspend fun upsertGame(rawGame: RawGame): GameWriteOutcome {
        val outcome = addGame(rawGame)

        // notify anyone interested that we've added a game
        repositoryScope.launch {
            val data = Data.Succeeded(getLatestAllGames(true, true))
            gamesLoadEvents.emit(data)
        }
        return outcome
    }

    /**
     * Synchronous core of [upsertGame]: convert + link + store the game in the in-memory maps,
     * without the load-event emit. `protected` so [RandomMemoryRepository] can seed a library from
     * its constructor, where a `suspend` call isn't possible and `runBlocking` isn't available in
     * commonMain. The lazy loaders ([getAllGames] etc.) read the maps on first subscribe, so the
     * seeded data still surfaces without the emit.
     */
    protected fun addGame(rawGame: RawGame): GameWriteOutcome {
        // Get and convert tracks
        val tracks = rawGame.tracks
            .map { it.toMemoryTrack() }
            // link this track to its artists
            .onEach { track -> linkToTrackFromItsArtists(track) }

        // get all converted artists
        val artists = tracks.asSequence()
            .map { it.artists }
            .flatten()
            .distinctBy { it.name }
            .toList()

        // convert game
        val game = MemoryGame(
            getNextPrimaryKey(),
            rawGame.title,
            rawGame.photoUrl,
            artists,
            tracks
        )

        // link each track to this game and add them to repository
        tracks.forEach { track ->
            track.game = game

            tracksById[track.id] = track
            tracksByTitle[track.title] = track
        }

        // link this game to its artist
        artists.forEach { artist ->
            artist.games.add(game)
        }

        // add game to repository
        gamesById[game.id] = game
        gamesByTitle[game.title] = game

        return GameWriteOutcome(game.id, GameWriteResult.ADDED)
    }

    private fun getLatestAllGames(withTracks: Boolean = false, withArtists: Boolean = false) = gamesByTitle
            .values
            .sortedBy { it.title }
            .map { it.toGame(withTracks, withArtists) }

    private fun RawTrack.toMemoryTrack(): MemoryTrack {
        val trackArtists = artist
            .split(DELIMITERS_ARTISTS)
            .map { it.trim() }
            .map { artistName -> getOrAddArtistByName(artistName) }

        return MemoryTrack(
            getNextPrimaryKey(),
            path,
            title,
            length,
            trackNumber,
            fadeLengthMs,
            null,
            trackArtists,
        )
    }

    private fun MemoryTrack.toTrack(
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): Track = Track(
        id,
        path,
        "",
        title,
        trackLengthMs,
        trackNumber,
        fadeLengthMs,
        if (withGame) game?.toGame() else null,
        if (withArtists) artists.map { it.toArtist() } else null,
        platform = Platform.OTHER,
        gameId = game?.id ?: 0L,
    )

    private fun MemoryGame.toGame(withTracks: Boolean = false, withArtists: Boolean = false): Game = Game(
            id,
            title,
            photoUrl,
            if (withArtists) artists.map { it.toArtist() } else null,
            if (withTracks) tracks.map { it.toTrack(withArtists = withArtists) } else null
        )

    private fun MemoryArtist.toArtist(
        withGames: Boolean = false,
        withTracks: Boolean = false
    ): Artist = Artist(
        id,
        name,
        photoUrl,
        if (withTracks) tracks.map { it.toTrack(withGame = withGames) } else null,
        if (withGames) games.map { it.toGame() } else null
    )

    private fun linkToTrackFromItsArtists(track: MemoryTrack) {
        track.artists
            .forEach { artist ->
                artist.tracks.add(track)
            }
    }

    private fun getOrAddArtistByName(name: String): MemoryArtist {
        var artist = artistsByName[name]

        if (artist != null) {
            return artist
        }

        val id = getNextPrimaryKey()
        artist = MemoryArtist(
            id,
            name,
            // Synthetic, non-null photo so the fake image loader renders generated art for artists
            // too (real artist photos are planned; the fake provides them now).
            "memory://artist/$id",
            mutableListOf(),
            mutableListOf()
        )

        artistsById[artist.id] = artist
        artistsByName[name] = artist

        return artist
    }

    private fun resetData() {
        gamesById.clear()
        tracksById.clear()
        artistsById.clear()

        gamesByTitle.clear()
        tracksByTitle.clear()
        artistsByName.clear()
    }

    private fun getNextPrimaryKey(): Long {
        lastPrimaryKey++
        return lastPrimaryKey
    }

    companion object {
        val DELIMITERS_ARTISTS = Regex(", &|,| or | and |&")
    }
}
