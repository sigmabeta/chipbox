package net.sigmabeta.chipbox.server.scanner

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.scanner.DuplicateRsnContentException
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.server.testing.RarEntry
import net.sigmabeta.chipbox.server.testing.allGames
import net.sigmabeta.chipbox.server.testing.allTracks
import net.sigmabeta.chipbox.server.testing.rar4
import net.sigmabeta.chipbox.server.testing.scanCorpus
import net.sigmabeta.chipbox.server.testing.spcFile
import net.sigmabeta.chipbox.server.testing.tempDir
import net.sigmabeta.sage.logging.BluntHatchet

/**
 * Two library folders holding byte-identical RSN archives must not collide on the unique
 * `(path, track_number)` index. The server refuses the second archive instead: the scan completes,
 * only one copy is indexed, and the failure is reported like any other unreadable file.
 *
 * These tests use real Room/SQLite — `FakeDatabase` does not enforce the unique index, so it cannot
 * reproduce the bug this guards against.
 */
class RsnDuplicateTest {

    @Test
    fun `identical archives in two folders are refused without failing the scan`() {
        val corpus = tempDir("rsn-duplicate-corpus")
        val workDir = tempDir("rsn-duplicate-work")
        val archive = rar4(
            RarEntry("01 First.spc", spcFile(title = "First")),
            RarEntry("02 Second.spc", spcFile(title = "Second")),
        )
        for (folder in listOf("Game A", "Game B")) {
            File(corpus, folder).mkdirs()
            File(corpus, "$folder/album.rsn").writeBytes(archive)
        }

        RealRepository().use { fixture ->
            val result = scanCorpus(
                corpus,
                workDir,
                ServerRsnExtractor(workDir, BluntHatchet()),
                fixture.repository,
            )

            // The duplicate is a per-file failure, not a fatal scan error: the rest of the library
            // still scans and the HTTP layer reports Complete with tracksFailed = 1.
            val complete = assertIs<ScannerState.Complete>(result.state)
            assertEquals(1, complete.gamesFound)
            assertEquals(2, complete.tracksFound)
            assertEquals(1, complete.tracksFailed)

            val games = result.repository.allGames()
            assertEquals(1, games.size, "only the owning folder may be indexed")
            val tracks = result.repository.allTracks()
            assertEquals(listOf("First", "Second"), tracks.map { it.title })
        }
    }

    @Test
    fun `a duplicate is refused even when the owning folder is skipped`() {
        val corpus = tempDir("rsn-skip-owner-corpus")
        val workDir = tempDir("rsn-skip-owner-work")
        val archive = rar4(RarEntry("01 First.spc", spcFile(title = "First")))
        val ownerFolder = File(corpus, "Game A").apply { mkdirs() }
        val archiveFile = File(ownerFolder, "album.rsn").apply { writeBytes(archive) }

        RealRepository().use { fixture ->
            val extractor = ServerRsnExtractor(workDir, BluntHatchet())
            val first = scanCorpus(corpus, workDir, extractor, fixture.repository)
            assertIs<ScannerState.Complete>(first.state)

            // Copy the archive into a new folder after the owner was indexed. The next scan skips
            // the unchanged owner entirely, so the duplicate check must come from the persisted
            // owner marker rather than "what this scan extracted".
            val copyFolder = File(corpus, "Game B").apply { mkdirs() }
            archiveFile.copyTo(File(copyFolder, "album.rsn"))

            val second = scanCorpus(corpus, workDir, extractor, fixture.repository)
            val complete = assertIs<ScannerState.Complete>(second.state)
            assertEquals(1, complete.tracksFailed)
            assertEquals(1, complete.tracksFound)
            assertEquals(1, fixture.repository.allGames().size)
            assertEquals(listOf("First"), fixture.repository.allTracks().map { it.title })
        }
    }

    @Test
    fun `renaming the owning folder adopts its extracted tracks`() {
        val corpus = tempDir("rsn-rename-corpus")
        val workDir = tempDir("rsn-rename-work")
        val archive = rar4(RarEntry("01 First.spc", spcFile(title = "First")))
        val oldFolder = File(corpus, "Game A").apply { mkdirs() }
        File(oldFolder, "album.rsn").writeBytes(archive)

        RealRepository().use { fixture ->
            val first = scanCorpus(
                corpus,
                workDir,
                ServerRsnExtractor(workDir, BluntHatchet()),
                fixture.repository,
            )
            assertIs<ScannerState.Complete>(first.state)
            val originalId = fixture.repository.allTracks().single().id

            // A folder rename (or deleting the owner and keeping the duplicate) leaves the old
            // game's extracted track rows in place until the post-sweep prune. The new game must
            // adopt them, keeping the id, instead of colliding on (path, trackNumber).
            val newFolder = File(corpus, "Game B").apply { mkdirs() }
            check(File(oldFolder, "album.rsn").renameTo(File(newFolder, "album.rsn")))
            check(oldFolder.delete())

            val second = scanCorpus(
                corpus,
                workDir,
                ServerRsnExtractor(workDir, BluntHatchet()),
                fixture.repository,
            )
            val complete = assertIs<ScannerState.Complete>(second.state)
            assertEquals(0, complete.tracksFailed)
            assertEquals(1, complete.gamesFound)
            assertEquals(1, fixture.repository.allGames().size, "the old game is pruned")
            val tracks = fixture.repository.allTracks()
            assertEquals(1, tracks.size)
            assertEquals(originalId, tracks.single().id, "the adopted row keeps its id")
        }
    }

    @Test
    fun `an owner that disappears releases the content hash`() = runBlocking {
        val corpus = tempDir("rsn-owner-corpus")
        val workDir = tempDir("rsn-owner-work")
        val archive = rar4(RarEntry("01 First.spc", spcFile(title = "First")))
        val owner = File(corpus, "Game A/album.rsn").apply {
            parentFile.mkdirs()
            writeBytes(archive)
        }
        val survivor = File(corpus, "Game B/album.rsn").apply {
            parentFile.mkdirs()
            writeBytes(archive)
        }
        val extractor = ServerRsnExtractor(workDir, BluntHatchet())

        assertEquals(1, extractor.extract(archive, owner.absolutePath)?.size)
        // Re-extracting the same source is idempotent, not a duplicate.
        assertEquals(1, extractor.extract(archive, owner.absolutePath)?.size)

        val duplicate = assertFailsWith<DuplicateRsnContentException> {
            extractor.extract(archive, survivor.absolutePath)
        }
        assertEquals(owner.absolutePath, duplicate.owner)
        assertEquals(survivor.absolutePath, duplicate.source)

        // The admin deleted the owning archive: the survivor takes the hash over.
        check(owner.delete())
        assertEquals(1, extractor.extract(archive, survivor.absolutePath)?.size)
    }
}

/** A real Room/SQLite repository, unlike the in-memory `FakeDatabase` which ignores indexes. */
private class RealRepository : AutoCloseable {
    private val database = Room.inMemoryDatabaseBuilder<ChipboxDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    val repository = DatabaseRepository(
        artistDao = database.artistDao(),
        gameDao = database.gameDao(),
        trackDao = database.trackDao(),
        gameArtistDao = database.gameArtistDao(),
        trackArtistDao = database.trackArtistDao(),
        searchHistoryDao = database.searchHistoryDao(),
        hatchet = BluntHatchet(),
    )

    override fun close() = database.close()
}
