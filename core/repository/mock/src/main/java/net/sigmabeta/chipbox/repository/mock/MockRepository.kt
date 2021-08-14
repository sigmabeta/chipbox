package net.sigmabeta.chipbox.repository.mock

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.RawGame
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
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

    private val gamesLoadEvents = MutableSharedFlow<Data<List<Game>>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    var maxGames = DEFAULT_MAX_GAMES
    var maxTracksPerGame = DEFAULT_MAX_TRACKS_PER_GAME

    override suspend fun getAllArtists(): List<Artist> {
        if (artists.isEmpty()) {
            generateGames()
        }

        return artists
            .sortedBy { it.name }
            .map { it.toArtist(true, true) }
    }

    override fun getAllGames(): Flow<Data<List<Game>>> {
        repositoryScope.launch {
            gamesLoadEvents.emit(Data.Loading)

            delay(3000L)

            val data = Data.Succeeded(getLatestAllGames())
            gamesLoadEvents.emit(data)
        }
        return gamesLoadEvents.asSharedFlow()
    }

    suspend fun getLatestAllGames(): List<Game> {
        if (games.isEmpty()) {
            generateGames()
        }

        return games
            .sortedBy { it.title }
            .map { it.toGame(true, true) }
    }

    override suspend fun getAllTracks(): List<Track> {
        if (tracks.isEmpty()) {
            generateGames()
        }

        return tracks
            .sortedBy { it.title }
            .map { it.toTrack(true, true) }
    }

    override suspend fun getGame(id: Long): Game? {
        if (games.isEmpty()) {
            generateGames()
        }

        return games
            .firstOrNull { it.id == id }
            ?.toGame(true, true)
    }

    override suspend fun getArtist(id: Long): Artist? {
        if (artists.isEmpty()) {
            generateGames()
        }

        return artists
            .firstOrNull { it.id == id }
            ?.toArtist(true, true)
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
            if (withArtists) artists.map { it.toArtist() } else null,
            if (withGame) game?.toGame() else null,
            trackLengthMs
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