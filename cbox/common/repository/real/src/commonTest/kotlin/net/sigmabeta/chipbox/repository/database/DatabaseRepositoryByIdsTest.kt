package net.sigmabeta.chipbox.repository.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.sage.logging.BluntHatchet

/**
 * Tests the surgical id-driven resolvers that replaced get-all-then-filter in the most-played /
 * game-of-the-day Home rows: `getGamesByIds`, `getArtistsByIds`, and the `getGameCount` +
 * `getGameAtIndex` pick. Each hydrates only what was asked for, never the whole catalog.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseRepositoryByIdsTest {

    @Test
    fun `getGamesByIds resolves only the requested games, skipping unknowns`() = runTest {
        val db = FakeDatabase()
        val repo = repo(db)
        val a = db.gameDao.insert(GameEntity("A", null, "/a", "sig"))
        db.gameDao.insert(GameEntity("B", null, "/b", "sig"))
        val c = db.gameDao.insert(GameEntity("C", null, "/c", "sig"))

        val result = repo.getGamesByIds(listOf(a, c, 999_999L)).first { it !is Data.Loading } as Data.Succeeded
        assertEquals(setOf(a, c), result.data.map { it.id }.toSet())
    }

    @Test
    fun `getArtistsByIds resolves only the requested artists`() = runTest {
        val db = FakeDatabase()
        val repo = repo(db)
        val a = db.artistDao.insert(ArtistEntity("Mitsuda", null))
        db.artistDao.insert(ArtistEntity("Uematsu", null))

        val result = repo.getArtistsByIds(listOf(a)).first { it !is Data.Loading } as Data.Succeeded
        assertEquals(listOf(a), result.data.map { it.id })
    }

    @Test
    fun `getGameCount and getGameAtIndex make a stable, in-range pick`() = runTest {
        val db = FakeDatabase()
        val repo = repo(db)
        val ids = (0 until 3).map { db.gameDao.insert(GameEntity("G$it", null, "/g$it", "sig")) }

        assertEquals(3, repo.getGameCount())
        // Ordered by id, so index maps to the matching insert.
        assertEquals(ids[0], repo.getGameAtIndex(0)?.id)
        assertEquals(ids[2], repo.getGameAtIndex(2)?.id)
        assertNull(repo.getGameAtIndex(3)) // out of range
    }

    private fun repo(db: FakeDatabase) = DatabaseRepository(
        artistDao = db.artistDao,
        gameDao = db.gameDao,
        trackDao = db.trackDao,
        gameArtistDao = db.gameArtistDao,
        trackArtistDao = db.trackArtistDao,
        searchHistoryDao = db.searchHistoryDao,
        hatchet = BluntHatchet(),
        dispatcher = UnconfinedTestDispatcher(),
    )
}
