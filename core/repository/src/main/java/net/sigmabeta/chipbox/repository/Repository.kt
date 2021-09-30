package net.sigmabeta.chipbox.repository

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
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

    fun getTrack(id: Long): Track?
}
