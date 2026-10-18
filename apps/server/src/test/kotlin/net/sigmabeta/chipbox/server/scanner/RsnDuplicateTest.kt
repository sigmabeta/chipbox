package net.sigmabeta.chipbox.server.scanner

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.models.HealthStatus
import net.sigmabeta.chipbox.scanner.DuplicateRsnContentException
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.server.http.toHealthStatus
import net.sigmabeta.chipbox.server.testing.RarEntry
import net.sigmabeta.chipbox.server.testing.RecordingHatchet
import net.sigmabeta.chipbox.server.testing.allGames
import net.sigmabeta.chipbox.server.testing.allTracks
import net.sigmabeta.chipbox.server.testing.newRepository
import net.sigmabeta.chipbox.server.testing.rar4
import net.sigmabeta.chipbox.server.testing.scanCorpus
import net.sigmabeta.chipbox.server.testing.spcFile
import net.sigmabeta.chipbox.server.testing.tempDir
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath

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
        val archive = twoMemberArchive()
        val owner = archiveIn(corpus, "Game A", archive)
        val duplicate = archiveIn(corpus, "Game B", archive)
        val hatchet = RecordingHatchet()

        RealRepository().use { fixture ->
            val result = scanCorpus(
                corpus,
                workDir,
                ServerRsnExtractor(workDir, hatchet),
                fixture.repository,
                hatchet,
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

            // The refusal is visible to the admin without any host path: the health mapping keeps
            // the count, and the wire JSON has no absolute path (or failed path) in it.
            val health = complete.toHealthStatus()
            assertEquals(1, health.tracksFailed)
            assertTrue(health.ok, "a refused archive does not make the server unhealthy")
            assertNull(health.state.failedFile)
            val json = Json.encodeToString(HealthStatus.serializer(), health)
            assertEquals(1, health.tracksFailed, json)
            assertTrue(json.contains("\"tracksFailed\":1"), json)
            assertFalse(json.contains(corpus.absolutePath), "health JSON leaked a host path: $json")

            // The log line names both archives, so the admin knows which pair to fix.
            val refusal = hatchet.errors.single { it.contains("Refusing RSN archive") }
            assertTrue(refusal.contains(owner.absolutePath), refusal)
            assertTrue(refusal.contains(duplicate.absolutePath), refusal)
        }
    }

    @Test
    fun `removing the refused duplicate yields a clean rescan with the survivor playable`() {
        val corpus = tempDir("rsn-remove-duplicate-corpus")
        val workDir = tempDir("rsn-remove-duplicate-work")
        val archive = twoMemberArchive()
        archiveIn(corpus, "Game A", archive)
        val duplicate = archiveIn(corpus, "Game B", archive)

        RealRepository().use { fixture ->
            val extractor = ServerRsnExtractor(workDir, BluntHatchet())
            val first = scanCorpus(corpus, workDir, extractor, fixture.repository)
            assertEquals(1, assertIs<ScannerState.Complete>(first.state).tracksFailed)
            val survivorPaths = fixture.repository.allTracks().map { it.path }
            assertTrue(
                survivorPaths.all { File(it).isFile },
                "the survivor's members are on disk after the refusal: $survivorPaths",
            )

            // The admin deletes the copy; the owning folder was skipped as unchanged, so only the
            // removed duplicate is re-read.
            check(duplicate.delete())

            val second = scanCorpus(corpus, workDir, extractor, fixture.repository)
            val complete = assertIs<ScannerState.Complete>(second.state)
            assertEquals(0, complete.tracksFailed, "no duplicate is left to refuse")
            assertEquals(1, fixture.repository.allGames().size)

            val tracks = fixture.repository.allTracks()
            assertEquals(2, tracks.size)
            assertEquals(
                survivorPaths.sorted(),
                tracks.map { it.path }.sorted(),
                "the surviving track rows still point at their extracted members",
            )
            assertTrue(tracks.all { File(it.path).isFile }, "every surviving member file still exists")
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
    fun `deleting the owning archive adopts the surviving duplicate's rows`() {
        val corpus = tempDir("rsn-delete-owner-corpus")
        val workDir = tempDir("rsn-delete-owner-work")
        val archive = twoMemberArchive()
        val owner = archiveIn(corpus, "Game A", archive)
        archiveIn(corpus, "Game B", archive)

        RealRepository().use { fixture ->
            val extractor = ServerRsnExtractor(workDir, BluntHatchet())
            val first = scanCorpus(corpus, workDir, extractor, fixture.repository)
            assertEquals(1, assertIs<ScannerState.Complete>(first.state).tracksFailed)
            val originalIds = fixture.repository.allTracks().associate { it.title to it.id }

            // The admin removes the owning archive (not a rename) and keeps the duplicate. The
            // old game's rows survive until the post-sweep prune; the new game must adopt them and
            // keep their ids, so favorites/queue references stay valid.
            check(owner.delete())
            check(owner.parentFile.delete())

            val second = scanCorpus(corpus, workDir, extractor, fixture.repository)
            val complete = assertIs<ScannerState.Complete>(second.state)
            assertEquals(0, complete.tracksFailed)
            assertEquals(1, complete.gamesFound)
            assertEquals(1, fixture.repository.allGames().size, "the removed owner's game is pruned")

            val tracks = fixture.repository.allTracks()
            assertEquals(originalIds, tracks.associate { it.title to it.id }, "both adopted rows keep their ids")
            assertTrue(tracks.all { File(it.path).isFile }, "the adopted rows point at existing members")
        }
    }

    @Test
    fun `an owner that disappears releases the content hash`() = runBlocking {
        val corpus = tempDir("rsn-owner-corpus")
        val workDir = tempDir("rsn-owner-work")
        val archive = rar4(RarEntry("01 First.spc", spcFile(title = "First")))
        val owner = archiveIn(corpus, "Game A", archive)
        val survivor = archiveIn(corpus, "Game B", archive)
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

    @Test
    fun `duplicate detection uses the injected filesystem, not the host`() = runBlocking {
        val corpus = tempDir("rsn-injected-fs-corpus")
        val workDir = tempDir("rsn-injected-fs-work")
        val archive = rar4(RarEntry("01 First.spc", spcFile(title = "First")))
        val owner = archiveIn(corpus, "Game A", archive)
        val survivor = archiveIn(corpus, "Game B", archive)

        // A forwarding filesystem that can hide the owner, standing in for a store whose view of
        // the library differs from the host filesystem's (a mount, a wrapper, an in-memory FS).
        // The owner check must ask THIS filesystem, never `java.io.File`.
        var ownerVisible = true
        var ownerQueries = 0
        val ownerPath = owner.toOkioPath()
        val filesystem = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun metadataOrNull(path: Path) =
                if (path == ownerPath) {
                    ownerQueries++
                    if (ownerVisible) super.metadataOrNull(path) else null
                } else {
                    super.metadataOrNull(path)
                }
        }
        val extractor = ServerRsnExtractor(workDir, BluntHatchet(), fileSystem = filesystem)
        assertEquals(1, extractor.extract(archive, owner.absolutePath)?.size)

        val duplicate = assertFailsWith<DuplicateRsnContentException> {
            extractor.extract(archive, survivor.absolutePath)
        }
        assertEquals(owner.absolutePath, duplicate.owner)
        assertTrue(ownerQueries > 0, "the owner check must go through the injected filesystem")

        // The filesystem now says the owner is gone while the host file is still there: the
        // survivor takes the hash over rather than being refused on stale host state.
        ownerVisible = false
        assertEquals(1, extractor.extract(archive, survivor.absolutePath)?.size)
    }

    private fun twoMemberArchive(): ByteArray = rar4(
        RarEntry("01 First.spc", spcFile(title = "First")),
        RarEntry("02 Second.spc", spcFile(title = "Second")),
    )

    private fun archiveIn(corpus: File, folder: String, bytes: ByteArray): File {
        val directory = File(corpus, folder).apply { mkdirs() }
        return File(directory, "album.rsn").apply { writeBytes(bytes) }
    }
}

/**
 * A real Room/SQLite repository, unlike the in-memory `FakeDatabase` which ignores indexes. The
 * DAO wiring is shared with every other server test through [newRepository].
 */
private class RealRepository : AutoCloseable {
    private val database = Room.inMemoryDatabaseBuilder<ChipboxDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    val repository = newRepository(database)

    override fun close() = database.close()
}
