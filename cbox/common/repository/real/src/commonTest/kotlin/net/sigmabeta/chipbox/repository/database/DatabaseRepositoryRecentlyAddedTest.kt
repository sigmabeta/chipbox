package net.sigmabeta.chipbox.repository.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.sage.logging.BluntHatchet

/**
 * Tests `getRecentlyAddedGames`: the surgical Home-row query keeps only games whose `date_added`
 * falls within the window and caps the result at the requested limit. The window/limit run in the
 * query, not in the caller, so the Home module never loads the whole catalog.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class DatabaseRepositoryRecentlyAddedTest {

    private val now = Clock.System.now().toEpochMilliseconds()
    private val oneDayMs = 24L * 60 * 60 * 1000
    private val oneWeekMs = 7L * oneDayMs

    @Test
    fun `keeps only games added within the window`() = runTest {
        val db = FakeDatabase()
        val repo = repo(db)
        val fresh = db.gameDao.insert(gameAddedDaysAgo("Fresh", 1))
        db.gameDao.insert(gameAddedDaysAgo("Stale", 10))
        db.gameDao.insert(GameEntity("NoDate", null, "/g/none", "sig")) // dateAdded == 0

        val result = repo.getRecentlyAddedGames(limit = 10, withinMs = oneWeekMs)
            .first { it !is Data.Loading } as Data.Succeeded

        assertEquals(setOf(fresh), result.data.map { it.id }.toSet())
    }

    @Test
    fun `caps the result at the requested limit`() = runTest {
        val db = FakeDatabase()
        val repo = repo(db)
        repeat(15) { db.gameDao.insert(gameAddedDaysAgo("Game$it", 1)) }

        val result = repo.getRecentlyAddedGames(limit = 10, withinMs = oneWeekMs)
            .first { it !is Data.Loading } as Data.Succeeded

        assertEquals(10, result.data.size)
    }

    @Test
    fun `reports Empty when nothing falls inside the window`() = runTest {
        val db = FakeDatabase()
        val repo = repo(db)
        db.gameDao.insert(gameAddedDaysAgo("Stale", 30))

        val settled = repo.getRecentlyAddedGames(limit = 10, withinMs = oneWeekMs).first { it !is Data.Loading }
        assertTrue(settled is Data.Empty)
    }

    private fun gameAddedDaysAgo(title: String, days: Int) = GameEntity(
        title = title,
        photoUrl = null,
        folderKey = "/g/$title",
        folderSignature = "sig",
        dateAdded = now - days * oneDayMs,
    )

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
