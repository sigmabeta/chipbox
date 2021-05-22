package net.sigmabeta.chipbox.repository

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track

interface Repository {
    suspend fun getAllArtists(): List<Artist>

    suspend fun getAllGames(): List<Game>

    suspend fun getAllTracks(): List<Track>
}
