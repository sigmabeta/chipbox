package net.sigmabeta.chipbox.repository.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
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
import timber.log.Timber

class DatabaseRepository(
    database: ChipboxDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Repository {
    private val artistDao = database.artistDao()
    private val gameDao = database.gameDao()
    private val trackDao = database.trackDao()

    private val gameArtistDao = database.gameArtistDao()
    private val trackArtistDao = database.trackArtistDao()


    override fun getAllArtists(
        withTracks: Boolean,
        withGames: Boolean
    ): Flow<Data<List<Artist>>> = setupFlow(
        { artistDao.getAll() },
        { list -> list.map { it.toArtist(withTracks, withGames) } }
    )

    override fun getAllGames(withTracks: Boolean, withArtists: Boolean) = setupFlow(
        { gameDao.getAll() },
        { list -> list.map { it.toGame(withTracks, withArtists) } }
    )

    override fun getAllTracks(withGame: Boolean, withArtists: Boolean) = trackDao
        .getAll()
        .map { list -> list.map { entity -> entity.toTrack(withGame, withArtists) } }
        .catch {
            Timber.e("Error: ${it.message}")
            Data.Failed<List<Game>>(it.message ?: ERR_UNKNOWN)
        }
        .map {
            if (it.isNotEmpty()) {
                Data.Succeeded(it)
            } else {
                Data.Empty
            }
        }

    override fun getTracksForGame(id: Long, withGame: Boolean, withArtists: Boolean) = trackDao
        .getTracksForGameSync(id)
        .map { entity -> entity.toTrack(withGame, withArtists) }

    override fun getGame(id: Long, withTracks: Boolean, withArtists: Boolean) = setupFlowWithId(
        id,
        { gameDao.getGame(id) },
        { it.toGame(withTracks, withArtists) }
    )

    override fun getArtist(
        id: Long,
        withTracks: Boolean,
        withGames: Boolean
    ): Flow<Data<Artist?>> = setupFlowWithId(
        id,
        { artistDao.getArtist(id) },
        { it.toArtist(withTracks, withGames) }
    )

    override fun getTrack(
        id: Long,
        withGame: Boolean,
        withArtists: Boolean
    ) = trackDao
        .getTrackSync(id)
        ?.toTrack(withGame, withArtists)


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

    private fun ArtistEntity.toArtist(
        withTracks: Boolean = false,
        withGames: Boolean = false
    ) =
        Artist(
            id,
            name,
            photoUrl,
            if (withTracks) getTracksForArtist(id) else null,
            if (withGames) getGamesForArtist(id) else null
        )

    private fun GameEntity.toGame(
        withTracks: Boolean = false,
        withArtists: Boolean = false
    ) = Game(
        id,
        title,
        photoUrl,
        if (withArtists) getArtistsForGame(id) else null,
        if (withTracks) getTracksForGame(id) else null
    )

    private fun TrackEntity.toTrack(
        withGame: Boolean = false,
        withArtists: Boolean = false
    ) =
        Track(
            id,
            path,
            title,
            trackLengthMs,
            trackNumber,
            fade,
            if (withGame) getGameById(game_id) else null,
            if (withArtists) getArtistsForTrack(id) else null,
        )

    private suspend fun RawTrack.toTrackEntityWithArtists(gameId: Long): Pair<TrackEntity, List<ArtistEntity>> {
        val trackArtists = getArtistsSplit()

        val tempTrack = TrackEntity(
            title,
            path,
            length,
            trackNumber,
            fade,
            gameId
        )

        val trackId = trackDao.insert(tempTrack)
        val insertedTrack = tempTrack.copy(id = trackId)

        insertedTrack.linkToTrackFromItsArtists(trackArtists)

        return insertedTrack to trackArtists
    }

    private suspend fun RawTrack.getArtistsSplit() = artist
        .split(*DELIMITERS_ARTISTS)
        .map { it.trim() }
        .map { artistName -> getOrAddArtistByName(artistName) }

    private fun TrackEntity.linkToTrackFromItsArtists(artists: List<ArtistEntity>) {
        val joins = artists
            .map { artist -> TrackArtistJoin(this.id, artist.id) }

        trackArtistDao.insertAll(joins)
    }

    private suspend fun getOrAddArtistByName(name: String): ArtistEntity {
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

    private fun getGameById(id: Long): Game = gameDao
        .getGameSync(id)
        .toGame()

    private fun getGamesForArtist(id: Long): List<Game> = gameArtistDao
        .getGamesForArtistSync(id)
        .map { it.toGame() }

    private fun getArtistsForTrack(id: Long): List<Artist> = trackArtistDao
        .getArtistsForTrackSync(id)
        .map { it.toArtist() }

    private fun getArtistsForGame(id: Long): List<Artist> = gameArtistDao
        .getArtistsForGameSync(id)
        .map { it.toArtist() }

    private fun getTracksForGame(id: Long): List<Track> = trackDao
        .getTracksForGameSync(id)
        .map { it.toTrack(withArtists = true) }

    private fun getTracksForArtist(id: Long): List<Track> = trackArtistDao
        .getTracksForArtistSync(id)
        .map { it.toTrack(withGame = true) }
        .sortedBy { it.game?.title }

    private fun resetData() {
        artistDao.nukeTable()
        gameDao.nukeTable()
        trackDao.nukeTable()

        gameArtistDao.nukeTable()
        trackArtistDao.nukeTable()
    }

    private fun <Entity, Model> setupFlow(
        databaseOp: () -> Flow<Entity>,
        converter: suspend (Entity) -> Model
    ): Flow<Data<Model>> {
        return databaseOp()
            .map { converter(it) }
            .catch {
                Timber.e("Error: ${it.message}")
                Data.Failed<List<Game>>(it.message ?: ERR_UNKNOWN)
            }
            .map { model ->
                if (model is List<*>) {
                    if (model.isNotEmpty()) {
                        Data.Succeeded(model)
                    } else {
                        Data.Empty
                    }
                } else {
                    if (model != null) {
                        Data.Succeeded(model)
                    } else {
                        Data.Empty
                    }
                }
            }
            .flowOn(dispatcher)
    }

    private fun <Entity, Model> setupFlowWithId(
        id: Long,
        databaseOp: (Long) -> Flow<Entity>,
        converter: suspend (Entity) -> Model
    ): Flow<Data<Model>> {
        return databaseOp(id)
            .map { converter(it) }
            .catch {
                Timber.e("Error: ${it.message}")
                Data.Failed<List<Game>>(it.message ?: ERR_UNKNOWN)
            }
            .map { model ->
                if (model is List<*>) {
                    if (model.isNotEmpty()) {
                        Data.Succeeded(model)
                    } else {
                        Data.Empty
                    }
                } else {
                    if (model != null) {
                        Data.Succeeded(model)
                    } else {
                        Data.Empty
                    }
                }
            }
            .flowOn(dispatcher)
    }

    companion object {
        const val ERR_UNKNOWN = "Unknown Error"

        val DELIMITERS_ARTISTS = arrayOf(", &", ",", " or ", " and ", "&")
    }
}
