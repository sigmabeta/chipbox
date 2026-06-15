package net.sigmabeta.chipbox.repository.memory

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.GameWriteResult
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [MemoryRepository] is the preview / test fake — the implemented methods drive every screenshot
 * test and Compose preview, so a regression here would silently desync them from the production
 * repository. These tests pin the implemented bits (the unimplemented `TODO()` methods are skipped
 * deliberately).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryRepositoryTest {

    @Test
    fun `upsertGame on a fresh repo returns ADDED and assigns a new id`() = runTest {
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        val outcome = repo.upsertGame(rawGame("Chrono Trigger", listOf(rawTrack("Schala", artist = "Yasunori Mitsuda"))))
        assertEquals(GameWriteResult.ADDED, outcome.result)
        assertTrue(outcome.gameId > 0, "gameId should be a generated primary key")
    }

    @Test
    fun `upsertGame populates getAllGames with the inserted game`() = runTest {
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        repo.upsertGame(rawGame("Chrono Trigger", listOf(rawTrack("Schala"))))

        // First non-Loading value from the flow is the populated game list.
        val data = repo.getAllGames(withTracks = true).first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded, "expected Succeeded, got $data")
        val games = data.data
        assertEquals(1, games.size)
        assertEquals("Chrono Trigger", games[0].title)
        assertEquals(1, games[0].tracks?.size)
        assertEquals("Schala", games[0].tracks?.first()?.title)
    }

    @Test
    fun `getAllGames before any upsert reports Empty`() = runTest {
        // Documented sentinel: empty lists come back as Data.Empty, not Data.Succeeded(emptyList).
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        val data = repo.getAllGames().first { it !is Data.Loading }
        assertEquals(Data.Empty, data)
    }

    @Test
    fun `upsertGame derives artists from comma and ampersand delimited names`() = runTest {
        // DELIMITERS_ARTISTS splits on ", & |,| or | and |&" — verify a multi-artist track
        // produces deduplicated artist entries that come back through getAllArtists.
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        repo.upsertGame(
            rawGame(
                "Game",
                listOf(rawTrack("Co-write", artist = "Composer A & Composer B")),
            ),
        )
        val data = repo.getAllArtists().first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded)
        val artists = data.data
        assertEquals(setOf("Composer A", "Composer B"), artists.map { it.name }.toSet())
    }

    @Test
    fun `getTrack returns the track by its assigned id`() = runTest {
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        repo.upsertGame(rawGame("Game", listOf(rawTrack("Track A"), rawTrack("Track B"))))
        val all = (repo.getAllTracks().first { it !is Data.Loading } as Data.Succeeded).data
        val trackA = all.first { it.title == "Track A" }
        val fetched = repo.getTrack(trackA.id)
        assertNotNull(fetched)
        assertEquals("Track A", fetched.title)
    }

    @Test
    fun `getTrack returns null for an unknown id`() = runTest {
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        assertNull(repo.getTrack(id = 99_999L))
    }

    @Test
    fun `clearLibrary empties every collection`() = runTest {
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        repo.upsertGame(rawGame("Game", listOf(rawTrack("Track"))))
        repo.clearLibrary()
        assertNull(repo.getTrack(id = 1L), "tracks must be cleared")
        // getAllArtists/Games/Tracks are gated by a one-shot 'loaded' flag inside the repo, so
        // we can't re-query them after clear without rebuilding — but the by-id lookups are
        // direct and reflect the cleared state.
    }

    @Test
    fun `addSearchHistory inserts queries in reverse chronological order`() = runTest {
        // Most-recently-added comes first per the prepended-list construction.
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        repo.addSearchHistory("first")
        repo.addSearchHistory("second")
        val data = repo.getSearchHistory().first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded)
        val history = data.data
        assertEquals(listOf("second", "first"), history.map { it.query })
    }

    @Test
    fun `addSearchHistory ignores a duplicate query`() = runTest {
        // De-duplication keeps the history from blooming with repeated identical searches.
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        repo.addSearchHistory("query")
        repo.addSearchHistory("query")
        val history = (repo.getSearchHistory().first { it is Data.Succeeded } as Data.Succeeded).data
        assertEquals(1, history.size)
    }

    @Test
    fun `removeSearchHistory drops the entry with the matching id`() = runTest {
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        repo.addSearchHistory("alpha")
        repo.addSearchHistory("beta")
        val withBoth = (repo.getSearchHistory().first { it is Data.Succeeded } as Data.Succeeded).data
        val betaId = withBoth.first { it.query == "beta" }.id
        repo.removeSearchHistory(betaId)
        val after = (repo.getSearchHistory().first { it is Data.Succeeded } as Data.Succeeded).data
        assertEquals(listOf("alpha"), after.map { it.query })
    }

    @Test
    fun `search matches games songs and artists by substring, Empty otherwise`() = runTest {
        // The fake searches its in-memory maps with a case-insensitive substring match on
        // title/name; a query with no match comes back as Data.Empty.
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        repo.upsertGame(rawGame("Chrono Trigger", listOf(rawTrack("Schala", artist = "Yasunori Mitsuda"))))

        val games = repo.searchGames("chrono").first { it !is Data.Loading }
        assertTrue(games is Data.Succeeded, "expected Succeeded, got $games")
        assertEquals("Chrono Trigger", games.data.single().title)

        val songs = repo.searchSongs("schal").first { it !is Data.Loading }
        assertTrue(songs is Data.Succeeded, "expected Succeeded, got $songs")
        assertEquals("Schala", songs.data.single().title)

        val artists = repo.searchArtists("mitsuda").first { it !is Data.Loading }
        assertTrue(artists is Data.Succeeded, "expected Succeeded, got $artists")
        assertEquals("Yasunori Mitsuda", artists.data.single().name)

        assertEquals(Data.Empty, repo.searchGames("nope").first { it !is Data.Loading })
        assertEquals(Data.Empty, repo.searchSongs("nope").first { it !is Data.Loading })
        assertEquals(Data.Empty, repo.searchArtists("nope").first { it !is Data.Loading })
    }

    @Test
    fun `folderSnapshots returns an empty map`() = runTest {
        // The fake repo has no folder reconciliation; documented to return empty.
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        assertTrue(repo.folderSnapshots().isEmpty())
    }

    @Test
    fun `pruneGames returns an empty list of removed titles`() = runTest {
        // Same — no pruning in the fake.
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        assertTrue(repo.pruneGames(setOf("any.folder.key")).isEmpty())
    }

    @Test
    fun `getAllTracks emits Empty when no upsert has happened`() = runTest {
        val repo = MemoryRepository(UnconfinedTestDispatcher(testScheduler))
        assertEquals(Data.Empty, repo.getAllTracks().first { it !is Data.Loading })
    }

    private fun rawGame(title: String, tracks: List<RawTrack>): RawGame = RawGame(
        title = title,
        photoUrl = null,
        folderKey = "/library/${title.lowercase()}",
        folderSignature = "sig-${title.hashCode()}",
        tracks = tracks,
    )

    private fun rawTrack(title: String, artist: String = "Unknown"): RawTrack = RawTrack(
        path = "/library/$title.psf",
        source = "test",
        title = title,
        artist = artist,
        game = "Game",
        length = 60_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
    )
}
