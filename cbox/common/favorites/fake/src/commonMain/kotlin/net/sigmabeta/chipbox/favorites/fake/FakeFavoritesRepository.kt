package net.sigmabeta.chipbox.favorites.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [net.sigmabeta.chipbox.favorites.FavoritesRepository] fake. Each `set…Favorite` updates a
 * [MutableStateFlow] of ids (newest favorite first), so the `is…Favorite` / `favorite…Ids` streams
 * emit reactively — enough to drive the real Compose UI over fakes. Tests can also seed/inspect the
 * lists directly.
 */
class FakeFavoritesRepository : net.sigmabeta.chipbox.favorites.FavoritesRepository {
    val trackIds = MutableStateFlow<List<Long>>(emptyList())
    val gameIds = MutableStateFlow<List<Long>>(emptyList())
    val artistIds = MutableStateFlow<List<Long>>(emptyList())

    override suspend fun setTrackFavorite(trackId: Long, favorite: Boolean) = trackIds.toggle(trackId, favorite)

    override suspend fun setGameFavorite(gameId: Long, favorite: Boolean) = gameIds.toggle(gameId, favorite)

    override suspend fun setArtistFavorite(artistId: Long, favorite: Boolean) = artistIds.toggle(artistId, favorite)

    override fun isTrackFavorite(trackId: Long): Flow<Boolean> = trackIds.map { trackId in it }

    override fun isGameFavorite(gameId: Long): Flow<Boolean> = gameIds.map { gameId in it }

    override fun isArtistFavorite(artistId: Long): Flow<Boolean> = artistIds.map { artistId in it }

    override fun favoriteTrackIds(): Flow<List<Long>> = trackIds.asStateFlow()

    override fun favoriteGameIds(): Flow<List<Long>> = gameIds.asStateFlow()

    override fun favoriteArtistIds(): Flow<List<Long>> = artistIds.asStateFlow()

    override suspend fun clearFavorites() {
        trackIds.value = emptyList()
        gameIds.value = emptyList()
        artistIds.value = emptyList()
    }

    // Newest favorite first: prepend on add, drop on remove.
    private fun MutableStateFlow<List<Long>>.toggle(id: Long, favorite: Boolean) = update { current ->
        if (favorite) listOf(id) + current.filterNot { it == id } else current.filterNot { it == id }
    }
}
