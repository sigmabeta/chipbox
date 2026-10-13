package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.sage.logging.BluntHatchet

/**
 * The snapshot-ingest contract, exercised against the production [DatabaseRepository] over the
 * in-memory DAO fakes ([FakeDatabase]) — the same rig the repository's own DatabaseRepositoryTest
 * uses. These assert external behaviour only: given a snapshot, what ends up in the library.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryImportTest {

    private val root = "/corpus"

    @Test
    fun `importing a snapshot into an empty library adds every game`() = runTest {
        val (repo, db) = newRepo()

        val result = repo.importLibrarySnapshot(snapshot(game("Chrono Trigger"), game("Secret of Mana")), root)

        assertEquals(LibraryImportResult(added = 2, updated = 0, unchanged = 0), result)
        assertEquals(setOf("Chrono Trigger", "Secret of Mana"), db.games.values.map { it.title }.toSet())
    }

    @Test
    fun `importing the same snapshot twice does not duplicate`() = runTest {
        val (repo, db) = newRepo()
        val snapshot = snapshot(game("Chrono Trigger", tracks = listOf(track("Schala"))))

        repo.importLibrarySnapshot(snapshot, root)
        val second = repo.importLibrarySnapshot(snapshot, root)

        assertEquals(LibraryImportResult(added = 0, updated = 0, unchanged = 1), second)
        assertEquals(1, db.games.size, "a re-import must reconcile, not duplicate")
        assertEquals(1, db.tracks.size)
    }

    @Test
    fun `importing upserts a game already in the library instead of adding a duplicate`() = runTest {
        val (repo, db) = newRepo()
        // The library already holds the game at its rebased folder path; the snapshot carries the
        // same folder with new metadata.
        repo.upsertGame(game("Old Title", folder = "Chrono Trigger"))

        val result = repo.importLibrarySnapshot(snapshot(game("New Title", folder = "Chrono Trigger")), root)

        assertEquals(LibraryImportResult(added = 0, updated = 1, unchanged = 0), result)
        assertEquals(1, db.games.size)
        assertEquals("New Title", db.games.values.single().title)
    }

    @Test
    fun `import does not require the audio files to exist on disk`() = runTest {
        val (repo, _) = newRepo()
        // The snapshot points at files under a corpus root that does not exist on this machine.
        val snapshot = snapshot(game("Chrono Trigger", tracks = listOf(track("Schala"))))

        val result = repo.importLibrarySnapshot(snapshot, root = "/nowhere/on/this/machine")

        assertEquals(1, result.added, "the absent files must not block the import")
        val games = repo.getAllGames(withTracks = true).first { it !is Data.Loading }
        assertTrue(games is Data.Succeeded)
        assertEquals("Chrono Trigger", games.data.single().title)
        assertEquals("Schala", games.data.single().tracks?.single()?.title)
    }

    @Test
    fun `import rebases corpus-relative paths onto the import root`() = runTest {
        val (repo, _) = newRepo()
        val snapshot = snapshot(game("Chrono Trigger", tracks = listOf(track("Schala"))))

        repo.importLibrarySnapshot(snapshot, root = "/device/corpus")

        val games = repo.getAllGames(withTracks = true).first { it !is Data.Loading }
        assertTrue(games is Data.Succeeded)
        assertEquals("/device/corpus/Chrono Trigger/Schala.psf", games.data.single().tracks?.single()?.path)
    }

    // ---- helpers ----

    private fun snapshot(vararg games: RawGame): LibrarySnapshot =
        librarySnapshot(root = root, scannerVersion = 1, readerVersions = mapOf("psf" to 1), games = games.toList())

    private fun game(title: String, folder: String = title, tracks: List<RawTrack> = emptyList()): RawGame = RawGame(
        title = title,
        photoUrl = null,
        // Absolute path under [root] — librarySnapshot() relativizes it for storage.
        folderKey = "$root/$folder",
        folderSignature = "sig-$folder",
        tracks = tracks,
    )

    private fun track(title: String): RawTrack = RawTrack(
        path = "$root/Chrono Trigger/$title.psf",
        source = "test",
        title = title,
        artist = "Yasunori Mitsuda",
        game = "Chrono Trigger",
        length = 60_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        extension = "psf",
        platform = Platform.PSX,
    )

    private fun newRepo(): Pair<DatabaseRepository, FakeDatabase> {
        val db = FakeDatabase()
        val repo = DatabaseRepository(
            artistDao = db.artistDao,
            gameDao = db.gameDao,
            trackDao = db.trackDao,
            gameArtistDao = db.gameArtistDao,
            trackArtistDao = db.trackArtistDao,
            searchHistoryDao = db.searchHistoryDao,
            hatchet = BluntHatchet(),
            dispatcher = UnconfinedTestDispatcher(),
        )
        return repo to db
    }
}
