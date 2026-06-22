package net.sigmabeta.chipbox.repository.database

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.entities.joins.TrackArtistJoin
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests `getTracksByIds`: it resolves only the requested rows (hydrated per the flags), skips
 * unknown ids, and reports [Data.Empty] when nothing matches — the cheap alternative to filtering
 * a full `getAllTracks`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseRepositoryTracksByIdsTest {

    @Test
    fun `resolves only the requested ids, hydrated with game, skipping unknowns`() = runTest {
        val db = FakeDatabase()
        val repo = repo(db)
        val gameId = db.gameDao.insert(GameEntity("Chrono Trigger", null, "/g", "sig"))
        val artistId = db.artistDao.insert(ArtistEntity("Mitsuda"))
        val ids = (0 until 3).map { n ->
            db.trackDao.insert(TrackEntity("T$n", "/g/$n", "", 1000L, n, 0L, gameId)).also { trackId ->
                db.trackArtistDao.insertAll(listOf(TrackArtistJoin(trackId, artistId)))
            }
        }

        // Ask for the 1st and 3rd tracks plus an id that doesn't exist.
        val result = repo.getTracksByIds(listOf(ids[0], ids[2], 999_999L), withGame = true)
            .first { it !is Data.Loading } as Data.Succeeded

        assertEquals(setOf(ids[0], ids[2]), result.data.map { it.id }.toSet())
        assertEquals(List(2) { "Chrono Trigger" }, result.data.map { it.game?.title })
    }

    @Test
    fun `reports Empty when none of the ids match`() = runTest {
        val db = FakeDatabase()
        val repo = repo(db)
        db.gameDao.insert(GameEntity("Game", null, "/g", "sig"))

        assertEquals(Data.Empty, repo.getTracksByIds(listOf(1L, 2L)).first { it !is Data.Loading })
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
