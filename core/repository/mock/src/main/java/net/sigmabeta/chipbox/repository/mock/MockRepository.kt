package net.sigmabeta.chipbox.repository.mock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Repository
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

class MockRepository(
    private val random: Random,
    private val seed: Long,
    private val stringGenerator: StringGenerator,
    private val mockImageUrlGenerator: MockImageUrlGenerator,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Repository {
    private var games: MutableList<Game> = mutableListOf()
    private var tracks: MutableList<Track> = mutableListOf()
    private var artists: MutableList<Artist> = mutableListOf()

    var maxGames = DEFAULT_MAX_GAMES
    var maxTracksPerGame = DEFAULT_MAX_TRACKS_PER_GAME

    override suspend fun getAllArtists(): List<Artist> {
        if (artists.isEmpty()) {
            generateGames()
        }
        return artists.sortedBy { it.name }
    }

    override suspend fun getAllGames(): List<Game> {
        if (games.isEmpty()) {
            generateGames()
        }
        return games.sortedBy { it.title }
    }

    override suspend fun getAllTracks(): List<Track> {
        if (tracks.isEmpty()) {
            generateGames()
        }
        return tracks.sortedBy { it.title }
    }

    override suspend fun getGame(id: Long): Game? {
        if (games.isEmpty()) {
            generateGames()
        }
        return games.firstOrNull { it.id == id }
    }

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

    private fun generateGame(): Game {
        val gameId = random.nextLong()

        val artistCount = getArtistCountForGame()

        val artistsForGame = mutableListOf<Artist>()

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

        val game = Game(
            gameId,
            stringGenerator.generateTitle(),
            artistsForGame,
            mockImageUrlGenerator.getGameImageUrl(random.nextInt()),
            tracks
        )

        game.tracks?.forEach {
            it.game = game
        }

        game.artists?.forEach {
            it.games?.add(game)
        }

        return game
    }

    private fun generateTracksForGame(possibleArtists: MutableList<Artist>): List<Track> {
        val trackCount = random.nextInt(maxTracksPerGame) + 1
        Timber.d("Generating $trackCount tracks...")

        val tracks = ArrayList<Track>()

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

            val track = generateTrack(trackNumber, trackArtists)
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

    private fun generateTrack(trackNumber: Int, artists: List<Artist>): Track {
        val track = Track(
            random.nextLong(),
            trackNumber,
            "",
            stringGenerator.generateTitle(),
            artists,
            null,
            random.nextInt(400000).toLong(),
            stringGenerator.generateTitle()
        )

        artists.forEach {
            (it.tracks as MutableList<Track>).add(track)
        }

        tracks.add(track)

        return track
    }

    private fun generateArtist(): Artist {
        val artist = Artist(
            random.nextLong(),
            stringGenerator.generateName(),
            mutableListOf(),
            mutableListOf(),
            mockImageUrlGenerator.getArtistImageUrl(random.nextInt())
        )

        artists.add(artist)

        return artist
    }

    private fun resetData() {
        games.clear()
        tracks.clear()
        artists.clear()
    }

    companion object {
        const val DEFAULT_MAX_GAMES = 100
        const val DEFAULT_MAX_TRACKS_PER_GAME = 30

        const val MAX_WORDS_PER_TITLE = 5
    }
}