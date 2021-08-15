package net.sigmabeta.chipbox.repository.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.entities.joins.GameArtistJoin
import net.sigmabeta.chipbox.entities.joins.TrackArtistJoin
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.Repository

class DatabaseRepository(
    database: ChipboxDatabase,
) : Repository {
    private val artistDao = database.artistDao()
    private val gameDao = database.gameDao()
    private val trackDao = database.trackDao()

    private val gameArtistDao = database.gameArtistDao()
    private val trackArtistDao = database.trackArtistDao()


    override suspend fun getAllArtists(): List<Artist> {
        TODO("Not yet implemented")
    }

    override fun getAllGames(): Flow<Data<List<Game>>> = gameDao
        .getAll()
        .map { list -> list.map { entity -> entity.toGame() } }
        .catch { Data.Failed<List<Game>>(it.message ?: ERR_UNKNOWN) }
        .map {
            if (it.isNotEmpty()) {
                Data.Succeeded(it)
            } else {
                Data.Empty
            }
        }

    override suspend fun getAllTracks(): List<Track> {
        TODO("Not yet implemented")
    }

    override suspend fun getGame(id: Long): Game? {
        TODO("Not yet implemented")
    }

    override suspend fun getArtist(id: Long): Artist? {
        TODO("Not yet implemented")
    }

    override suspend fun addGame(rawGame: RawGame) {
        // Insert game..
        val game = GameEntity(
            rawGame.title,
            rawGame.photoUrl
        )

        val gameId = gameDao.insert(game)

        // Insert & return tracks & artists.
        val trackAndArtists = rawGame.tracks
            .map { it.toTrackEntityWithArtists(gameId) }

        // Link this game to its artists.
        val gameArtistJoins = trackAndArtists
            .map { it.second }
            .flatten()
            .distinctBy { it.id }
            .map { artist -> GameArtistJoin(gameId, artist.id) }

        gameArtistDao.insertAll(gameArtistJoins)
    }

    private fun GameEntity.toGame(): Game =
        Game(
            id,
            title,
            photoUrl,
            null,
            null
        )

    private fun RawTrack.toTrackEntityWithArtists(gameId: Long): Pair<TrackEntity, List<ArtistEntity>> {
        val trackArtists = getArtistsSplit()

        val tempTrack = TrackEntity(
            title,
            path,
            length,
            gameId
        )

        val trackId = trackDao.insert(tempTrack)
        val insertedTrack = tempTrack.copy(id = trackId)

        insertedTrack.linkToTrackFromItsArtists(trackArtists)

        return insertedTrack to trackArtists
    }

    private fun RawTrack.getArtistsSplit() = artist
        .split(*DELIMITERS_ARTISTS)
        .map { it.trim() }
        .map { artistName -> getOrAddArtistByName(artistName) }

    private fun TrackEntity.linkToTrackFromItsArtists(artists: List<ArtistEntity>) {
        val joins = artists
            .map { artist -> TrackArtistJoin(this.id, artist.id) }

        trackArtistDao.insertAll(joins)
    }

    private fun getOrAddArtistByName(name: String): ArtistEntity {
        var artist = artistDao.getArtistByNameSync(name)

        if (artist != null) {
            return artist
        }

        artist = ArtistEntity(
            name,
            null
        )

        val id = artistDao.insert(artist)

        return artist.copy(id = id)
    }

    private fun resetData() {
        artistDao.nukeTable()
        gameDao.nukeTable()
        trackDao.nukeTable()

        gameArtistDao.nukeTable()
        trackArtistDao.nukeTable()
    }

    companion object {
        const val ERR_UNKNOWN = "Unknown Error"

        val DELIMITERS_ARTISTS = arrayOf(", &", ",", " or ", " and ", "&")
    }
}