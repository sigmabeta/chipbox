package net.sigmabeta.chipbox.repository.mock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.mock.models.MockArtist
import net.sigmabeta.chipbox.repository.mock.models.MockGame
import net.sigmabeta.chipbox.repository.mock.models.MockTrack
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringGenerator
import java.util.Locale
import java.util.Random

class MockRepository(
    private val random: Random,
    private val seed: Long,
    private val stringGenerator: StringGenerator,
    private val mockImageUrlGenerator: MockImageUrlGenerator,
    private val hatchet: Hatchet,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Repository {
    private val repositoryScope = CoroutineScope(dispatcher)

    private var games: MutableList<MockGame> = mutableListOf()
    private var tracks: MutableList<MockTrack> = mutableListOf()
    private var artists: MutableList<MockArtist> = mutableListOf()

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

    // TODO this is a garbage idea. will result in screens loading the wrong data. oh well lol
    private val singleArtistLoadEvents = MutableSharedFlow<Data<Artist>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val singleGameLoadEvents = MutableSharedFlow<Data<Game>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val singleTrackLoadEvents = MutableSharedFlow<Data<Track>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    var maxGames = DEFAULT_MAX_GAMES
    private var maxTracksPerGame = DEFAULT_MAX_TRACKS_PER_GAME

    suspend fun getLatestAllGames(
        withTracks: Boolean = false,
        withArtists: Boolean = false
    ): List<Game> {
        if (games.isEmpty()) {
            generateGames()
        }

        return games
            .sortedBy { it.title }
            .map { it.toGame(withTracks, withArtists) }
    }

    override fun getAllArtists(withTracks: Boolean, withGames: Boolean): Flow<Data<List<Artist>>> {
        if (artists.isEmpty()) {
            repositoryScope.launch {
                artistsLoadEvents.emit(Data.Loading)
                generateGames()

                val data = artists
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }
                    .map { it.toArtist(withGames, withTracks) }

                artistsLoadEvents.emit(Data.Succeeded(data))
            }
        }

        return artistsLoadEvents.asSharedFlow()
    }

    override fun getAllGames(withTracks: Boolean, withArtists: Boolean): Flow<Data<List<Game>>> {
        if (games.isEmpty()) {
            repositoryScope.launch {
                gamesLoadEvents.emit(Data.Loading)
                gamesLoadEvents.emit(
                    Data.Succeeded(
                        getLatestAllGames(withTracks, withArtists)
                    )
                )
            }
        }
        return gamesLoadEvents.asSharedFlow()
    }

    override fun getAllTracks(withGame: Boolean, withArtists: Boolean): Flow<Data<List<Track>>> {
        if (tracks.isEmpty()) {
            repositoryScope.launch {
                tracksLoadEvents.emit(Data.Loading)
                generateGames()

                val data = tracks
                    .sortedBy { it.title }
                    .map { it.toTrack(withGame, withArtists) }

                tracksLoadEvents.emit(Data.Succeeded(data))
            }
        }

        return tracksLoadEvents.asSharedFlow()
    }

    override fun getTracksForGame(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> {
        TODO("Not yet implemented")
    }

    override fun getTracksForArtist(id: Long, withGame: Boolean, withArtists: Boolean): List<Track> {
        TODO("Not yet implemented")
    }

    override fun getTracksForPlatform(
        platform: Platform,
        withGame: Boolean,
        withArtists: Boolean
    ): List<Track> {
        TODO("Not yet implemented")
    }

    override fun getGamesForPlatform(platform: Platform): Flow<Data<List<Game>>> {
        TODO("Not yet implemented")
    }

    override fun getAvailablePlatforms(): Flow<Data<List<Platform>>> {
        TODO("Not yet implemented")
    }

    override fun getGame(id: Long, withTracks: Boolean, withArtists: Boolean): Flow<Data<Game?>> {
        if (games.isEmpty()) {
            repositoryScope.launch {
                singleGameLoadEvents.emit(Data.Loading)

                generateGames()

                val data = games
                    .first { it.id == id }
                    .toGame(withTracks, withArtists)

                singleGameLoadEvents.emit(Data.Succeeded(data))
            }
        }
        return singleGameLoadEvents.asSharedFlow()
    }

    override fun getArtist(id: Long, withTracks: Boolean, withGames: Boolean): Flow<Data<Artist?>> {
        if (artists.isEmpty()) {
            repositoryScope.launch {
                singleArtistLoadEvents.emit(Data.Loading)

                generateGames()

                val data = artists
                    .first { it.id == id }
                    .toArtist(withTracks, withGames)

                singleArtistLoadEvents.emit(Data.Succeeded(data))
            }
        }
        return singleArtistLoadEvents.asSharedFlow()
    }

    override fun getTrack(
        id: Long,
        withGame: Boolean,
        withArtists: Boolean
    ) = tracks
        .firstOrNull { it.id == id }
        ?.toTrack(withGame, withArtists)

    override suspend fun addGame(rawGame: RawGame) = Unit

    override suspend fun clearLibrary() {
        resetData()
    }

    override fun searchGames(query: String): Flow<Data<List<Game>>> = flowOf(Data.Empty)

    override fun searchSongs(query: String): Flow<Data<List<Track>>> = flowOf(Data.Empty)

    override fun searchArtists(query: String): Flow<Data<List<Artist>>> = flowOf(Data.Empty)

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

    private suspend fun generateGames() {
        withContext(dispatcher) {
            resetData()

            random.setSeed(seed)

            val gameCount = random.nextInt(maxGames)
            hatchet.i("Generating $gameCount games...")

            for (gameIndex in 0 until gameCount) {
                val game = generateGame()
                games.add(game)
            }

//        delay(4000)

            hatchet.i("Generated ${games.size} games...")
        }
    }

    private fun generateGame(): MockGame {
        val gameId = random.nextLong()

        val artistCount = getArtistCountForGame()

        val artistsForGame = mutableListOf<MockArtist>()

        for (artistIndex in 0 until artistCount) {
            val artistToAdd = if (shouldGenerateNewArtist()) {
                generateArtist()
            } else {
                if (artists.isEmpty()) {
                    generateArtist()
                } else {
                    artists.random()
                }
            }
            artistsForGame.add(artistToAdd)
        }

        val tracks = generateTracksForGame(artistsForGame)

        val game = MockGame(
            gameId,
            stringGenerator.generateTitle(),
            mockImageUrlGenerator.getGameImageUrl(random.nextInt()),
            artistsForGame,
            tracks
        )

        game.tracks.forEach {
            it.game = game
        }

        game.artists.forEach {
            it.games?.add(game)
        }

        return game
    }

    private fun generateTracksForGame(possibleArtists: List<MockArtist>): List<MockTrack> {
        val trackCount = random.nextInt(maxTracksPerGame) + 1
        hatchet.d("Generating $trackCount tracks...")

        val tracks = mutableListOf<MockTrack>()

        for (trackNumber in 1..trackCount) {
            val trackArtists = if (shouldTrackHaveOneArtist()) {
                if (possibleArtists.isEmpty()) {
                    possibleArtists
                } else {
                    listOf(possibleArtists.random())
                }
            } else {
                possibleArtists
            }

            val track = generateTrack(trackArtists)
            tracks.add(track)
        }

        return tracks
    }

    private fun shouldTrackHaveOneArtist() = random.nextInt(RANDOM_BOUND) >= SINGLE_ARTIST_THRESHOLD

    private fun shouldGenerateNewArtist() = random.nextInt(RANDOM_BOUND) >= NEW_ARTIST_THRESHOLD

    private fun getArtistCountForGame(): Int {
        val randomNumber = random.nextInt(RANDOM_BOUND)
        return when {
            randomNumber < 1 -> 0
            randomNumber < 2 -> MULTI_ARTIST_COUNT
            randomNumber < MULTI_ARTIST_ROLL_MAX -> 2
            else -> 1
        }
    }

    private fun generateTrack(artists: List<MockArtist>): MockTrack {
        val track = MockTrack(
            random.nextLong(),
            "",
            stringGenerator.generateTitle(),
            random.nextInt(MAX_TRACK_LENGTH_MS).toLong(),
            -1,
            0L,
            null,
            artists
        )

        artists.forEach {
            (it.tracks as MutableList<MockTrack>).add(track)
        }

        tracks.add(track)

        return track
    }

    private fun generateArtist(): MockArtist {
        val artist = MockArtist(
            random.nextLong(),
            stringGenerator.generateName(),
            mockImageUrlGenerator.getArtistImageUrl(random.nextInt()),
            mutableListOf(),
            mutableListOf()
        )

        artists.add(artist)

        return artist
    }

    private fun resetData() {
        games.clear()
        tracks.clear()
        artists.clear()
    }

    private fun MockTrack.toTrack(withGame: Boolean = false, withArtists: Boolean = false): Track = Track(
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
        )

    private fun MockGame.toGame(withTracks: Boolean = false, withArtists: Boolean = false): Game = Game(
            id,
            title,
            photoUrl,
            if (withArtists) artists.map { it.toArtist() } else null,
            if (withTracks) tracks.map { it.toTrack() } else null
        )

    private fun MockArtist.toArtist(
        withGames: Boolean = false,
        withTracks: Boolean = false
    ): Artist = Artist(
        id,
        name,
        photoUrl,
        if (withTracks) tracks.map { it.toTrack() } else null,
        if (withGames) games.map { it.toGame() } else null
    )

    companion object {
        const val DEFAULT_MAX_GAMES = 100
        const val DEFAULT_MAX_TRACKS_PER_GAME = 30

        private const val RANDOM_BOUND = 10
        private const val SINGLE_ARTIST_THRESHOLD = 5
        private const val NEW_ARTIST_THRESHOLD = 3
        private const val MULTI_ARTIST_COUNT = 3
        private const val MULTI_ARTIST_ROLL_MAX = 4
        private const val MAX_TRACK_LENGTH_MS = 400000
    }
}
