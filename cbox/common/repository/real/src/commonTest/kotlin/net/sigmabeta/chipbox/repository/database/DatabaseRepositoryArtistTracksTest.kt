package net.sigmabeta.chipbox.repository.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.database.dao.GameDao
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.entities.joins.TrackArtistJoin
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.sage.logging.BluntHatchet

/**
 * Locks in the artist-detail hydration fix: loading an artist's tracks resolves every track's game
 * in **one** batched query, not a `getGameSync` round-trip per track (the N+1 the handoff
 * `arch-docs/artist-detail-loading-stall.md` described).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseRepositoryArtistTracksTest {

    @Test
    fun `an artist's tracks are hydrated with a single batched game lookup`() = runTest {
        val db = FakeDatabase()
        val countingGameDao = CountingGameDao(db.gameDao)
        val repo = repo(db, countingGameDao)

        val artistId = db.artistDao.insert(ArtistEntity("Prolific Composer"))
        val gameIds = (0 until 5).map { db.gameDao.insert(GameEntity("Game$it", null, "/g$it", "sig")) }
        // 20 tracks spread across 5 games (4 each), all credited to the one artist.
        repeat(20) { n ->
            val trackId = db.trackDao.insert(
                TrackEntity("T$n", "/g/$n", "", 1_000L, n, 0L, gameIds[n % gameIds.size]),
            )
            db.trackArtistDao.insertAll(listOf(TrackArtistJoin(trackId, artistId)))
        }

        val artist = repo.getArtist(artistId, withTracks = true, withGames = false)
            .first { it is Data.Succeeded } as Data.Succeeded

        val tracks = artist.data!!.tracks.orEmpty()
        assertEquals(20, tracks.size)
        assertTrue(tracks.all { it.game != null }, "every track should carry its hydrated game")
        // One batched query for the five distinct games; no per-row getGameSync fan-out.
        assertEquals(1, countingGameDao.byIdsSyncCalls)
        assertEquals(0, countingGameDao.byIdSyncCalls)
    }

    private fun repo(db: FakeDatabase, gameDao: GameDao) = DatabaseRepository(
        artistDao = db.artistDao,
        gameDao = gameDao,
        trackDao = db.trackDao,
        gameArtistDao = db.gameArtistDao,
        trackArtistDao = db.trackArtistDao,
        searchHistoryDao = db.searchHistoryDao,
        hatchet = BluntHatchet(),
        dispatcher = UnconfinedTestDispatcher(),
    )

    private class CountingGameDao(private val delegate: GameDao) : GameDao by delegate {
        var byIdSyncCalls = 0
            private set
        var byIdsSyncCalls = 0
            private set

        override suspend fun getGameSync(gameId: Long): GameEntity {
            byIdSyncCalls++
            return delegate.getGameSync(gameId)
        }

        override suspend fun getGamesByIdsSync(ids: List<Long>): List<GameEntity> {
            byIdsSyncCalls++
            return delegate.getGamesByIdsSync(ids)
        }
    }
}
