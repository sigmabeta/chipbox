package net.sigmabeta.chipbox.repository.mock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Repository
import timber.log.Timber
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class MockRepository(
    private val random: Random,
    private val seed: Long,
    private val stringGenerator: StringGenerator,
    private val mockImageUrlGenerator: MockImageUrlGenerator,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Repository {
    private var possibleTags: Map<String, List<String>>? = null

    private var possibleArtists: List<Artist>? = null

    private var remainingTracks: List<Track>? = null

    var generateEmptyState = false

    var maxTracks = DEFAULT_MAX_TRACKS
    var maxGames = DEFAULT_MAX_GAMES
    var maxArtists = DEFAULT_MAX_COMPOSERS
    var maxTrackssPerGame = DEFAULT_MAX_TRACKS_PER_GAME


    override fun getAllArtists(): List<Artist> {
        TODO("Not yet implemented")
    }

    override suspend fun getAllGames(): List<Game> = generateGames()

    private suspend fun generateGames() = withContext(dispatcher) {
        possibleTags = null
        possibleArtists = null
        remainingTracks = null

        random.setSeed(seed)

        if (generateEmptyState) {
            throw IOException("Arbitrarily failed a network request!")
        }

        val gameCount = random.nextInt(maxGames)
        Timber.i("Generating $gameCount games...")
        val games = ArrayList<Game>(gameCount)

        for (gameIndex in 0 until gameCount) {
            val game = generateGame()
            games.add(game)
        }

//        delay(4000)

        Timber.i("Generated ${games.size} games...")

        val filteredGames = games
            .distinctBy { it.id }
            .filter { !it.tracks.isNullOrEmpty() }

        Timber.i("Returning ${filteredGames.size} games...")
        filteredGames.sortedBy { it.title }
    }

    private fun generateGame(): Game {
        val gameId = random.nextLong()
        val tracks = getTracksForGame()
        val artists = tracks
            .map { it.artist?.split(",") }
            .flatMap { it?.toList() ?: emptyList() }
            .distinct()

        var variousArtists = false
        val artist = when (artists.size) {
            0 -> null
            1 -> artists.first()
            else -> {
                variousArtists = true
                "Various Artists"
            }
        }

        return Game(
            gameId,
            stringGenerator.generateTitle(),
            artist,
            variousArtists,
            mockImageUrlGenerator.getGameImageUrl(random.nextInt()),
            tracks
        )
    }

    private fun getTracksForGame(): List<Track> {
        if (remainingTracks == null) {
            generateTracks()
        }

        val availableTracks = remainingTracks!!.toMutableList()
        val trackCount = random.nextInt(maxTrackssPerGame) + 1

        val tracks = ArrayList<Track>(trackCount)

        val randomNumber = random.nextInt(10)
        val artistCount = when {
            randomNumber < 1 -> 0
            randomNumber < 2 -> 3
            randomNumber < 4 -> 2
            else -> 1
        }

        val artistsForGame = mutableListOf<Artist>()

        for (artistIndex in 0 until artistCount) {
            artistsForGame.add(possibleArtists!!.random())
        }

        for (trackIndex in 0 until trackCount) {
            var removeIndex = -1
            availableTracks.forEachIndexed { index, track ->
                if (artistsForGame.isEmpty()) {
                    if (track.artist == null) {
                        tracks.add(track)
                        removeIndex = index
                        return@forEachIndexed
                    }
                }

                if (artistsForGame.size == 1) {
                    artistsForGame.forEach {
                        if (track.artist == it.name) {
                            tracks.add(track)
                            removeIndex = index
                            return@forEachIndexed
                        }
                    }
                } else {
                    artistsForGame.forEach {
                        if (track.artist?.contains(it.name) == true) {
                            tracks.add(track)
                            removeIndex = index
                            return@forEachIndexed
                        }
                    }
                }
            }

            if (removeIndex >= 0) {
                availableTracks.removeAt(removeIndex)
            }
        }

        return tracks
    }

    private fun generateTracks() {
        val trackCount = random.nextInt(maxTracks) + 1
        Timber.d("Generating $trackCount tracks...")

        val tracks = ArrayList<Track>()
        val trackIds = HashSet<Long>(trackCount)

        for (trackIndex in 0 until trackCount) {
            val track = generateTrack()
            val newId = track.id

            if (!trackIds.contains(newId)) {
                tracks.add(track)
                trackIds.add(newId)
            }
        }

        remainingTracks = tracks
    }

    private fun generateTrack() = Track(
        random.nextLong(),
        random.nextInt(maxTrackssPerGame),
        "",
        stringGenerator.generateTitle(),
        stringGenerator.generateTitle(),
        getArtistsForTrack(),
        stringGenerator.generateTitle()
    )

    @Suppress("MagicNumber")
    private fun getArtistsForTrack(): String? {
        if (possibleArtists == null) {
            generateArtists()
        }

        val availableArtists = possibleArtists!!
        val randomNumber = random.nextInt(10)
        val artistCount = when {
            randomNumber < 1 -> return null
            randomNumber < 2 -> 3
            randomNumber < 4 -> 2
            else -> 1
        }

        val artists = ArrayList<Artist>(artistCount)

        for (artistIndex in 0 until artistCount) {
            val whichArtist = random.nextInt(availableArtists.size - 1)
            val artist = availableArtists.get(whichArtist)
            artists.add(artist)
        }

        return artists
            .distinctBy { it.id }
            .joinToString { it.name }
    }

    private fun generateArtists() {
        val artistCount = random.nextInt(maxArtists) + 1
        Timber.i("Generating $artistCount artists...")
        val artists = ArrayList<Artist>(artistCount)

        for (artistIndex in 0 until artistCount) {
            val artist = generateArtist()
            artists.add(artist)
        }

        possibleArtists = artists.distinctBy { it.id }
    }

    private fun generateArtist() = Artist(
        random.nextLong(),
        stringGenerator.generateName(),
        null
    )

    companion object {
        const val DEFAULT_MAX_TRACKS = 2000
        const val DEFAULT_MAX_GAMES = 100
        const val DEFAULT_MAX_COMPOSERS = 200
        const val DEFAULT_MAX_TRACKS_PER_GAME = 30

        const val MAX_WORDS_PER_TITLE = 5
    }
}