package net.sigmabeta.chipbox.repository.memory

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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

    // TODO Garbage idea. Wrong data will load on second instance of a screen
    private var artistsLoaded = false
    private var gamesLoaded = false
    private var tracksLoaded = false
    private var singleGameLoaded = false
    private var singleArtistLoaded = false

    override fun getAllArtists(
        withTracks: Boolean,
        withGames: Boolean
    ): Flow<Data<List<Artist>>> {
        if (!artistsLoaded) {
            artistsLoaded = true
            repositoryScope.launch {
                artistsLoadEvents.emit(Data.Loading)

                val artists = artistsByName
                    .values
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }
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

    override fun getAllGames(withTracks: Boolean, withArtists: Boolean): Flow<Data<List<Game>>> {
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
        withArtists: Boolean
    ): Flow<Data<List<Track>>> {
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

    override fun getGame(
        id: Long,
        withTracks: Boolean,
        withArtists: Boolean
    ): Flow<Data<Game?>> {
        if (!singleGameLoaded) {
            singleGameLoaded = true
            repositoryScope.launch {
                singleGameLoadEvents.emit(Data.Loading)

                val game = gamesById[id]
                    ?.toGame(withTracks, withArtists)

                val data = if (game != null) {
                    Data.Succeeded(game)
                } else {
                    Data.Empty
                }

                singleGameLoadEvents.emit(data)
            }
        }
        return singleGameLoadEvents.asSharedFlow()
    }

    override fun getArtist(
        id: Long,
        withTracks: Boolean,
        withGames: Boolean
    ): Flow<Data<Artist?>> {
        if (!singleArtistLoaded) {
            singleArtistLoaded = true
            repositoryScope.launch {
                singleArtistLoadEvents.emit(Data.Loading)

                val artist = artistsById[id]
                    ?.toArtist(true, true)

                val data = if (artist != null) {
                    Data.Succeeded(artist)
                } else {
                    Data.Empty
                }

                singleArtistLoadEvents.emit(data)
            }
        }
        return singleArtistLoadEvents.asSharedFlow()
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
            val data = Data.Succeeded(getLatestAllGames(true, true))
            gamesLoadEvents.emit(data)
        }
    }

    private fun getLatestAllGames(withTracks: Boolean = false, withArtists: Boolean = false) =
        gamesByTitle
            .values
            .sortedBy { it.title }
            .map { it.toGame(withTracks, withArtists) }

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
            fade,
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
        trackLengthMs,
        fade,
        if (withGame) game?.toGame() else null,
        if (withArtists) artists.map { it.toArtist() } else null
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