package net.sigmabeta.chipbox.favorites

import kotlinx.coroutines.flow.Flow

/**
 * Facade over the favorites database. Toggles favorite state for tracks, games, and artists, exposes
 * the per-item "is favorite" streams the CTAs observe and the id lists the Favorites screen (and the
 * director's favorites session) read, and clears all favorites.
 *
 * Reads return bare library ids — callers hydrate them into full models via the library `Repository`
 * (the favorites database holds no model data, only ids, since the library is a separately-rebuilt
 * cache).
 */
interface FavoritesRepository {
    suspend fun setTrackFavorite(trackId: Long, favorite: Boolean)

    suspend fun setGameFavorite(gameId: Long, favorite: Boolean)

    suspend fun setArtistFavorite(artistId: Long, favorite: Boolean)

    fun isTrackFavorite(trackId: Long): Flow<Boolean>

    fun isGameFavorite(gameId: Long): Flow<Boolean>

    fun isArtistFavorite(artistId: Long): Flow<Boolean>

    /** Favorited track ids, newest favorite first. */
    fun favoriteTrackIds(): Flow<List<Long>>

    /** Favorited game ids, newest favorite first. */
    fun favoriteGameIds(): Flow<List<Long>>

    /** Favorited artist ids, newest favorite first. */
    fun favoriteArtistIds(): Flow<List<Long>>

    /** Wipe every favorite — tracks, games, and artists. */
    suspend fun clearFavorites()
}
