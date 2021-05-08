package net.sigmabeta.chipbox.repository

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game

interface Repository {
    fun getAllArtists(): List<Artist>

    suspend fun getAllGames(): List<Game>
}