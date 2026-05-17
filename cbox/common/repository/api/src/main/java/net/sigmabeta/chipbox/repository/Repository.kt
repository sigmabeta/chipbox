package net.sigmabeta.chipbox.repository

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.SearchHistory
import net.sigmabeta.chipbox.models.Track

interface Repository {
    // Lists
    fun getAllArtists(
        withTracks: Boolean = false,
        withGames: Boolean = false
    ): Flow<Data<List<Artist>>>

    fun getAllGames(
        withTracks: Boolean = false,
        withArtists: Boolean = false
    ): Flow<Data<List<Game>>>

    fun getAllTracks(
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): Flow<Data<List<Track>>>

    fun getTracksForGame(
        id: Long,
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): List<Track>

    fun getTracksForArtist(
        id: Long,
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): List<Track>

    fun getTracksForPlatform(
        platform: Platform,
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): List<Track>

    fun getGamesForPlatform(platform: Platform): Flow<Data<List<Game>>>

    fun getAvailablePlatforms(): Flow<Data<List<Platform>>>

    // Individual models
    fun getGame(
        id: Long,
        withTracks: Boolean = false,
        withArtists: Boolean = false
    ): Flow<Data<Game?>>

    fun getArtist(
        id: Long,
        withTracks: Boolean = false,
        withGames: Boolean = false
    ): Flow<Data<Artist?>>

    suspend fun addGame(rawGame: RawGame)

    fun getTrack(
        id: Long,
        withGame: Boolean = false,
        withArtists: Boolean = false
    ): Track?

    suspend fun clearLibrary()

    // Search
    fun searchGames(query: String): Flow<Data<List<Game>>>

    fun searchSongs(query: String): Flow<Data<List<Track>>>

    fun searchArtists(query: String): Flow<Data<List<Artist>>>

    // Search history
    fun getSearchHistory(): Flow<Data<List<SearchHistory>>>

    suspend fun addSearchHistory(query: String)

    suspend fun removeSearchHistory(id: Long)
}
