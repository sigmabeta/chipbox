package net.sigmabeta.chipbox.repository.memory

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
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.memory.models.MemoryArtist
import net.sigmabeta.chipbox.repository.memory.models.MemoryGame
import net.sigmabeta.chipbox.repository.memory.models.MemoryTrack
import java.util.*

class MemoryRepository(
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Repository {
    private val repositoryScope = CoroutineScope(dispatcher)

    private var gamesByTitle = mutableMapOf<String, MemoryGame>()
    private var tracksByTitle = mutableMapOf<String, MemoryTrack>()
    private var artistsByName = mutableMapOf<String, MemoryArtist>()

    private var gamesById = mutableMapOf<Long, MemoryGame>()
    private var tracksById = mutableMapOf<Long, MemoryTrack>()
    private var artistsById = mutableMapOf<Long, MemoryArtist>()

    private var lastPrimaryKey = 0L

    private val gamesLoadEvents = MutableSharedFlow<Data<List<Game>>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var gamesLoaded = false

    override suspend fun getAllArtists(): List<Artist> {
        return artistsByName
            .values
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
            .map { it.toArtist(true, true) }
    }

    override fun getAllGames(): Flow<Data<List<Game>>> {
        if (!gamesLoaded) {
            gamesLoaded = true
            repositoryScope.launch {
                gamesLoadEvents.emit(Data.Loading)

                delay(3000L)

                val games = getLatestAllGames()
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

    override suspend fun getAllTracks(): List<Track> {
        return tracksByTitle
            .values
            .sortedBy { it.title }
            .map { it.toTrack(true, true) }
    }

    override suspend fun getGame(id: Long): Game? {
        return gamesById[id]
            ?.toGame(true, true)
    }

    override suspend fun getArtist(id: Long): Artist? {
        return artistsById[id]
            ?.toArtist(true, true)
    }

    override suspend fun addGame(rawGame: RawGame) {
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

        // notify anyone interested that we've added a game
        repositoryScope.launch {
            val data = Data.Succeeded(getLatestAllGames())
            gamesLoadEvents.emit(data)
        }
    }

    private fun getLatestAllGames() = gamesByTitle
        .values
        .sortedBy { it.title }
        .map { it.toGame(true, true) }

    private fun RawTrack.toMemoryTrack(): MemoryTrack {
        val trackArtists = artist
            .split(*DELIMITERS_ARTISTS)
            .map { it.trim() }
            .map { artistName -> getOrAddArtistByName(artistName) }

        return MemoryTrack(
            getNextPrimaryKey(),
            path,
            title,
            trackArtists,
            null,
            length
        )
    }

    private fun MemoryTrack.toTrack(
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): Track = Track(
        id,
        path,
        title,
        if (withArtists) artists.map { it.toArtist() } else null,
        if (withGame) game?.toGame() else null,
        trackLengthMs
    )

    private fun MemoryGame.toGame(withTracks: Boolean = false, withArtists: Boolean = false): Game =
        Game(
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

        artist = MemoryArtist(
            getNextPrimaryKey(),
            name,
            null,
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
        val DELIMITERS_ARTISTS = arrayOf(", &", ",", " or ", " and ", "&")
    }
}