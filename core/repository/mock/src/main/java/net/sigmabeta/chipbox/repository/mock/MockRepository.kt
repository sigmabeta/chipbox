package net.sigmabeta.chipbox.repository.mock

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Inject

class MockRepository @Inject constructor(val stringGenerator: StringGenerator): Repository {
    override fun getAllArtists(): List<Artist> {
        TODO("Not yet implemented")
    }

    override fun getAllGames(): List<Game> {
        TODO("Not yet implemented")
    }

    companion object {
        const val DEFAULT_MAX_SONGS = 50
        const val DEFAULT_MAX_GAMES = 400
        const val DEFAULT_MAX_COMPOSERS = 200
        const val DEFAULT_MAX_SONGS_PER_GAME = 30

        const val MAX_WORDS_PER_TITLE = 5
    }
}