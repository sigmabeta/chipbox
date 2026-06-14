package net.sigmabeta.chipbox.uitest

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.memory.RandomMemoryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Contract for [RandomMemoryRepository]: it produces exactly the requested sizes and is
 * deterministic for a given seed. (Lives here for now because the repository/fake module has no
 * test source set; it could move there if one is added.)
 */
class RandomMemoryRepositoryTest {
    @Test
    fun defaultsProduceRequestedCounts() = runBlocking {
        val repo = RandomMemoryRepository()

        assertEquals(RandomMemoryRepository.DEFAULT_GAMES, gameTitles(repo).size)
        assertEquals(RandomMemoryRepository.DEFAULT_ARTISTS, artistCount(repo))
        assertEquals(RandomMemoryRepository.DEFAULT_TRACKS, trackCount(repo))
    }

    @Test
    fun sameSeedProducesIdenticalLibrary() = runBlocking {
        val first = gameTitles(RandomMemoryRepository(seed = 99))
        val second = gameTitles(RandomMemoryRepository(seed = 99))

        assertEquals(first, second)
    }

    @Test
    fun differentSeedProducesDifferentLibrary() = runBlocking {
        val a = gameTitles(RandomMemoryRepository(seed = 1))
        val b = gameTitles(RandomMemoryRepository(seed = 2))

        assertNotEquals(a, b)
    }

    private suspend fun gameTitles(repo: RandomMemoryRepository): List<String> = withTimeout(TIMEOUT_MS) {
        val games = repo.getAllGames(withTracks = false, withArtists = false)
            .first { it is Data.Succeeded } as Data.Succeeded<List<Game>>
        games.data.map { it.title }
    }

    private suspend fun artistCount(repo: RandomMemoryRepository): Int = withTimeout(TIMEOUT_MS) {
        val artists = repo.getAllArtists(withTracks = false, withGames = false)
            .first { it is Data.Succeeded }
        (artists as Data.Succeeded).data.size
    }

    private suspend fun trackCount(repo: RandomMemoryRepository): Int = withTimeout(TIMEOUT_MS) {
        val tracks = repo.getAllTracks(withGame = false, withArtists = false)
            .first { it is Data.Succeeded }
        (tracks as Data.Succeeded).data.size
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
