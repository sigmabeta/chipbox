package net.sigmabeta.chipbox.favorites.real

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.ArtistFavoriteEntity
import net.sigmabeta.chipbox.entities.GameFavoriteEntity
import net.sigmabeta.chipbox.entities.TrackFavoriteEntity
import net.sigmabeta.chipbox.favorites.FavoritesRepository
import net.sigmabeta.chipbox.favorites.dao.ArtistFavoriteDao
import net.sigmabeta.chipbox.favorites.dao.GameFavoriteDao
import net.sigmabeta.chipbox.favorites.dao.TrackFavoriteDao
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production [FavoritesRepository]. Each `set…Favorite` inserts a row stamped with the current
 * wall-clock time (so the favorites lists order newest-first) or deletes it. Room dispatches the
 * suspend DAO calls off the caller thread, so no explicit dispatcher hop is needed here.
 */
@OptIn(ExperimentalTime::class)
class RealFavoritesRepository(
    private val trackDao: TrackFavoriteDao,
    private val gameDao: GameFavoriteDao,
    private val artistDao: ArtistFavoriteDao,
    private val hatchet: Hatchet,
) : FavoritesRepository {

    override suspend fun setTrackFavorite(trackId: Long, favorite: Boolean) {
        if (favorite) {
            trackDao.add(TrackFavoriteEntity(trackId, now()))
        } else {
            trackDao.remove(trackId)
        }
        hatchet.d("Track $trackId favorite=$favorite")
    }

    override suspend fun setGameFavorite(gameId: Long, favorite: Boolean) {
        if (favorite) {
            gameDao.add(GameFavoriteEntity(gameId, now()))
        } else {
            gameDao.remove(gameId)
        }
        hatchet.d("Game $gameId favorite=$favorite")
    }

    override suspend fun setArtistFavorite(artistId: Long, favorite: Boolean) {
        if (favorite) {
            artistDao.add(ArtistFavoriteEntity(artistId, now()))
        } else {
            artistDao.remove(artistId)
        }
        hatchet.d("Artist $artistId favorite=$favorite")
    }

    override fun isTrackFavorite(trackId: Long): Flow<Boolean> = trackDao.isFavorite(trackId)

    override fun isGameFavorite(gameId: Long): Flow<Boolean> = gameDao.isFavorite(gameId)

    override fun isArtistFavorite(artistId: Long): Flow<Boolean> = artistDao.isFavorite(artistId)

    override fun favoriteTrackIds(): Flow<List<Long>> = trackDao.getAllIds()

    override fun favoriteGameIds(): Flow<List<Long>> = gameDao.getAllIds()

    override fun favoriteArtistIds(): Flow<List<Long>> = artistDao.getAllIds()

    override suspend fun clearFavorites() {
        trackDao.nukeTable()
        gameDao.nukeTable()
        artistDao.nukeTable()
        hatchet.i("Cleared favorites.")
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
