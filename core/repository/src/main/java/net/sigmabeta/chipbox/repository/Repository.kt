package net.sigmabeta.chipbox.repository

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.RawGame
import net.sigmabeta.chipbox.models.Track

interface Repository {
    // Lists
    suspend fun getAllArtists(): List<Artist>

    suspend fun getAllGames(): List<Game>

    suspend fun getAllTracks(): List<Track>

    // Individual models
    suspend fun getGame(id: Long): Game?

    suspend fun getArtist(id: Long): Artist?

    suspend fun addGame(rawGame: RawGame)
}
