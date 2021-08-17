package net.sigmabeta.chipbox.repository.mock

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.mock.models.MockArtist
import net.sigmabeta.chipbox.repository.mock.models.MockGame
import net.sigmabeta.chipbox.repository.mock.models.MockTrack
import timber.log.Timber
import java.util.*

class MockRepository(
    private val random: Random,
    private val seed: Long,
    private val stringGenerator: StringGenerator,
    private val mockImageUrlGenerator: MockImageUrlGenerator,
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

    override suspend fun addGame(rawGame: RawGame) = Unit

    private suspend fun generateGames() {
        withContext(dispatcher) {
            resetData()

            random.setSeed(seed)

            val gameCount = random.nextInt(maxGames)
            Timber.i("Generating $gameCount games...")

            for (gameIndex in 0 until gameCount) {
                val game = generateGame()
                games.add(game)
            }

//        delay(4000)

            Timber.i("Generated ${games.size} games...")
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
        Timber.d("Generating $trackCount tracks...")

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

    private fun shouldTrackHaveOneArtist() = random.nextInt(10) >= 5

    private fun shouldGenerateNewArtist() = random.nextInt(10) >= 3

    private fun getArtistCountForGame(): Int {
        val randomNumber = random.nextInt(10)
        return when {
            randomNumber < 1 -> 0
            randomNumber < 2 -> 3
            randomNumber < 4 -> 2
            else -> 1
        }
    }

    private fun generateTrack(artists: List<MockArtist>): MockTrack {
        val track = MockTrack(
            random.nextLong(),
            "",
            stringGenerator.generateTitle(),
            artists,
            false,
            null,
            random.nextInt(400000).toLong()
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

    private fun MockTrack.toTrack(withGame: Boolean = false, withArtists: Boolean = false): Track =
        Track(
            id,
            path,
            title,
            trackLengthMs,
            fade,
            if (withGame) game?.toGame() else null,
            if (withArtists) artists.map { it.toArtist() } else null
        )

    private fun MockGame.toGame(withTracks: Boolean = false, withArtists: Boolean = false): Game =
        Game(
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

        const val MAX_WORDS_PER_TITLE = 5
    }
}