package net.sigmabeta.chipbox.repository.memory

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.models.*
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.memory.models.MemoryArtist
import net.sigmabeta.chipbox.repository.memory.models.MemoryGame
import net.sigmabeta.chipbox.repository.memory.models.MemoryTrack

class MemoryRepository(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Repository {
    private var gamesByTitle = mutableMapOf<String, MemoryGame>()
    private var tracksByTitle = mutableMapOf<String, MemoryTrack>()
    private var artistsByName = mutableMapOf<String, MemoryArtist>()

    private var gamesById = mutableMapOf<Long, MemoryGame>()
    private var tracksById = mutableMapOf<Long, MemoryTrack>()
    private var artistsById = mutableMapOf<Long, MemoryArtist>()

    private var lastPrimaryKey = 0L

    override suspend fun getAllArtists(): List<Artist> {
        return artistsByName
            .toList()
            .map { it.second }
            .sortedBy { it.name }
            .map { it.toArtist(true, true) }
    }

    override suspend fun getAllGames(): List<Game> {
        return gamesByTitle
            .toList()
            .map { it.second }
            .sortedBy { it.title }
            .map { it.toGame(true, true) }
    }

    override suspend fun getAllTracks(): List<Track> {
        return tracksByTitle
            .toList()
            .map { it.second }
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
        val tracks = rawGame.tracks
            .map { it.toMemoryTrack() }
            .onEach { track -> linkToTrackFromItsArtists(track) }

        val artists = tracks.asSequence()
            .map { it.artists }
            .flatten()
            .distinctBy { it.name }
            .toList()

        val game = MemoryGame(
            getNextPrimaryKey(),
            rawGame.title,
            rawGame.photoUrl,
            artists,
            tracks
        )

        tracks.forEach { track ->
            track.game = game

            tracksById[track.id] = track
            tracksByTitle[track.title] = track
        }

        artists.forEach { artist ->
            artist.games.add(game)
        }

        gamesById[game.id] = game
        gamesByTitle[game.title] = game
    }

    private fun RawTrack.toMemoryTrack(): MemoryTrack {
        val trackArtists = artist
            .split(*DELIMITERS_ARTISTS)
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
            if (withTracks) tracks.map { it.toTrack() } else null
        )

    private fun MemoryArtist.toArtist(
        withGames: Boolean = false,
        withTracks: Boolean = false
    ): Artist = Artist(
        id,
        name,
        photoUrl,
        if (withTracks) tracks.map { it.toTrack() } else null,
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