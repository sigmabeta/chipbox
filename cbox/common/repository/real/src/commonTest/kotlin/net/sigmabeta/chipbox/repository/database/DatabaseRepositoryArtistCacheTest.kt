package net.sigmabeta.chipbox.repository.database

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.database.dao.ArtistDao
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.entities.joins.TrackArtistJoin
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks in the artist-by-id caching contract: when hydrating many tracks that share an artist, the
 * artist row is read from storage once (and reused), not once per track. Wraps [FakeDatabase]'s
 * [ArtistDao] in a counter and asserts on `getArtistByIdSync` call counts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseRepositoryArtistCacheTest {

    @Test
    fun `a shared artist is read from storage once across many tracks`() = runTest {
        val db = FakeDatabase()
        val countingArtistDao = CountingArtistDao(db.artistDao)
        val repo = repo(db, countingArtistDao)

        // One game, three tracks, all by the same single artist.
        val gameId = db.gameDao.insert(GameEntity("Game", null, "/g", "sig"))
        val artistId = db.artistDao.insert(ArtistEntity("Shared Artist"))
        repeat(3) { n ->
            val trackId = db.trackDao.insert(TrackEntity("T$n", "/g/$n", "", 1000L, n, 0L, gameId))
            db.trackArtistDao.insertAll(listOf(TrackArtistJoin(trackId, artistId)))
        }

        val tracks = repo.getTracksForGame(gameId, withArtists = true)

        // Every track is hydrated with the shared artist...
        assertEquals(3, tracks.size)
        assertEquals(List(3) { listOf("Shared Artist") }, tracks.map { t -> t.artists?.map { it.name } })
        // ...but storage was hit once, not once per track.
        assertEquals(1, countingArtistDao.byIdCalls)
    }

    @Test
    fun `distinct artists are each read once`() = runTest {
        val db = FakeDatabase()
        val countingArtistDao = CountingArtistDao(db.artistDao)
        val repo = repo(db, countingArtistDao)

        val gameId = db.gameDao.insert(GameEntity("Game", null, "/g", "sig"))
        val artistA = db.artistDao.insert(ArtistEntity("Artist A"))
        val artistB = db.artistDao.insert(ArtistEntity("Artist B"))
        // Three tracks: two by A, one by both A and B → two distinct artists overall.
        val t0 = db.trackDao.insert(TrackEntity("T0", "/g/0", "", 1000L, 0, 0L, gameId))
        val t1 = db.trackDao.insert(TrackEntity("T1", "/g/1", "", 1000L, 1, 0L, gameId))
        val t2 = db.trackDao.insert(TrackEntity("T2", "/g/2", "", 1000L, 2, 0L, gameId))
        db.trackArtistDao.insertAll(
            listOf(
                TrackArtistJoin(t0, artistA),
                TrackArtistJoin(t1, artistA),
                TrackArtistJoin(t2, artistA),
                TrackArtistJoin(t2, artistB),
            )
        )

        repo.getTracksForGame(gameId, withArtists = true)

        // A and B each fetched once despite four (track, artist) links.
        assertEquals(2, countingArtistDao.byIdCalls)
    }

    private fun repo(db: FakeDatabase, artistDao: ArtistDao) = DatabaseRepository(
        artistDao = artistDao,
        gameDao = db.gameDao,
        trackDao = db.trackDao,
        gameArtistDao = db.gameArtistDao,
        trackArtistDao = db.trackArtistDao,
        searchHistoryDao = db.searchHistoryDao,
        hatchet = BluntHatchet(),
        dispatcher = UnconfinedTestDispatcher(),
    )

    private class CountingArtistDao(private val delegate: ArtistDao) : ArtistDao by delegate {
        var byIdCalls = 0
            private set

        override suspend fun getArtistByIdSync(artistId: Long): ArtistEntity {
            byIdCalls++
            return delegate.getArtistByIdSync(artistId)
        }
    }
}
