package net.sigmabeta.chipbox.server.scanner

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.models.HealthStatus
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.FolderSnapshot
import net.sigmabeta.chipbox.repository.GameWriteOutcome
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.server.http.toHealthStatus
import net.sigmabeta.chipbox.server.testing.RealRepository
import net.sigmabeta.chipbox.server.testing.RecordingHatchet
import net.sigmabeta.chipbox.server.testing.allGames
import net.sigmabeta.chipbox.server.testing.allTracks
import net.sigmabeta.chipbox.server.testing.spcFile
import net.sigmabeta.chipbox.server.testing.tempDir
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * Library locations must be disjoint. A root configured twice, or one root inside another, would
 * make [LocalFileContentSource] emit the shared folders twice in one scan; the parallel readers
 * then race on the unique `game.folder_key` index and the whole scan aborts. The scanner refuses
 * the configuration before it walks, writes, or prunes, so the stored library survives intact.
 *
 * These drive the real okio-backed source over a temp corpus, so canonicalization (the hand-edited
 * locations-file case) is part of what is covered. The repository is real Room/SQLite:
 * `FakeDatabase` is not safe for the scanner's parallel folder readers, and the accepted-scan
 * cases exercise exactly that concurrency.
 */
class OverlappingRootsTest {

    @Test
    fun `a nested root fails the scan and leaves the stored library untouched`() {
        val corpus = tempDir("overlap-nested-corpus")
        val workDir = tempDir("overlap-nested-work")
        libraryFolder(File(corpus, "Game A"), title = "First")

        RealRepository().use { real ->
            val repository = CountingRepository(seededLibrary(real.repository))
            val hatchet = RecordingHatchet()
            // The admin added the corpus root and then a folder inside it (or hand-edited the list).
            val state = scan(
                locationsFile(workDir, corpus, File(corpus, "Game A")),
                repository,
                hatchet,
            )

            assertIs<ScannerState.Failed>(state)
            assertEquals(0, repository.upserts, "a refused configuration writes nothing")
            assertEquals(0, repository.prunes, "the prune must not run, so stored games survive")
            assertEquals(0, repository.snapshotReads, "the library is not even read")

            val games = real.repository.allGames()
            assertEquals(listOf("Existing Game"), games.map { it.title })
            assertEquals(listOf("Existing Track"), real.repository.allTracks().map { it.title })

            // Both roots are named in full so the admin knows which pair to fix.
            val refusal = hatchet.errors.single { it.contains("Refusing library scan") }
            assertTrue(refusal.contains(corpus.absolutePath), refusal)
            assertTrue(refusal.contains(File(corpus, "Game A").absolutePath), refusal)
        }
    }

    @Test
    fun `the same root twice fails the scan with zero repository writes`() {
        val corpus = tempDir("overlap-duplicate-corpus")
        val workDir = tempDir("overlap-duplicate-work")
        libraryFolder(File(corpus, "Game A"), title = "First")

        RealRepository().use { real ->
            val repository = CountingRepository(seededLibrary(real.repository))
            val state = scan(locationsFile(workDir, corpus, corpus), repository, RecordingHatchet())

            assertIs<ScannerState.Failed>(state)
            assertEquals(0, repository.upserts)
            assertEquals(0, repository.prunes)
            assertEquals(listOf("Existing Game"), real.repository.allGames().map { it.title })
        }
    }

    @Test
    fun `the health body carries no host path for the refusal`() {
        val corpus = tempDir("overlap-health-corpus")
        val workDir = tempDir("overlap-health-work")
        libraryFolder(File(corpus, "Game A"), title = "First")

        RealRepository().use { real ->
            val state = scan(
                locationsFile(workDir, corpus, File(corpus, "Game A")),
                CountingRepository(seededLibrary(real.repository)),
                RecordingHatchet(),
            )

            val health = assertIs<ScannerState.Failed>(state).toHealthStatus()
            assertFalse(health.ok)
            assertEquals(null, health.state.failedFile, "a configuration failure has no failing file")
            val json = Json.encodeToString(HealthStatus.serializer(), health)
            assertFalse(json.contains(corpus.absolutePath), "health JSON leaked a host path: $json")
            assertFalse(json.contains("Game A"), "health JSON leaked a folder name: $json")
        }
    }

    @Test
    fun `two disjoint roots scan exactly as before`() {
        val corpus = tempDir("overlap-disjoint-corpus")
        val workDir = tempDir("overlap-disjoint-work")
        libraryFolder(File(corpus, "SNES/Game A"), title = "First")
        libraryFolder(File(corpus, "Genesis/Game B"), title = "Second")

        RealRepository().use { real ->
            val state = scan(
                locationsFile(workDir, File(corpus, "SNES"), File(corpus, "Genesis")),
                CountingRepository(seededLibrary(real.repository)),
                RecordingHatchet(),
            )

            val complete = assertIs<ScannerState.Complete>(state)
            assertEquals(2, complete.gamesFound)
            assertEquals(2, complete.tracksFound)
            assertEquals(0, complete.tracksFailed)
            // The seeded game was pruned: it is not under either configured root any more.
            assertEquals(listOf("First", "Second"), real.repository.allTracks().map { it.title }.sorted())
        }
    }

    @Test
    fun `a single root scans exactly as before`() {
        val corpus = tempDir("overlap-single-corpus")
        val workDir = tempDir("overlap-single-work")
        libraryFolder(File(corpus, "Game A"), title = "First")
        libraryFolder(File(corpus, "Game B"), title = "Second")

        RealRepository().use { real ->
            val state = scan(
                locationsFile(workDir, corpus),
                CountingRepository(seededLibrary(real.repository)),
                RecordingHatchet(),
            )

            val complete = assertIs<ScannerState.Complete>(state)
            assertEquals(2, complete.gamesFound)
            assertEquals(2, complete.tracksFound)
            assertEquals(0, complete.tracksFailed)
            assertEquals(listOf("First", "Second"), real.repository.allTracks().map { it.title }.sorted())
        }
    }

    // ---- fixtures ----

    /** Seeds a stored game/track, used to prove a refused scan neither writes nor prunes. */
    private fun seededLibrary(repository: Repository): Repository {
        runBlocking {
            repository.upsertGame(
                RawGame(
                    title = "Existing Game",
                    photoUrl = null,
                    folderKey = "/previous/library/Existing",
                    folderSignature = "existing-signature",
                    tracks = listOf(
                        RawTrack(
                            path = "/previous/library/Existing/01.spc",
                            source = "file",
                            title = "Existing Track",
                            artist = "Artist",
                            game = "Existing Game",
                            length = 10_000L,
                            trackNumber = 0,
                            fadeLengthMs = 0L,
                            extension = "spc",
                        ),
                    ),
                ),
            )
        }
        return repository
    }

    private fun libraryFolder(directory: File, title: String) {
        directory.mkdirs()
        File(directory, "01 First.spc").writeBytes(spcFile(title = title))
    }

    /** Writes the persisted locations file the way an admin edit (or an older app) would. */
    private fun locationsFile(workDir: File, vararg roots: File) =
        File(workDir, "locations.txt").apply {
            writeText(roots.joinToString("\n") { it.absolutePath })
        }

    private fun scan(
        locations: File,
        repository: Repository,
        hatchet: Hatchet,
    ): ScannerState {
        val source = LocalFileContentSource(FileSystem.SYSTEM, locations.toOkioPath())
        val scanner = RealScanner(
            repository = repository,
            librarySource = source,
            readers = Readers(hatchet),
            hatchet = hatchet,
        )
        return runBlocking {
            scanner.startScan()
            scanner.state().first { it is ScannerState.Complete || it is ScannerState.Failed }
        }
    }

    /** Counts the writes a refused scan must never make, then delegates to the real repository. */
    private class CountingRepository(private val delegate: Repository) : Repository by delegate {
        var upserts = 0
            private set
        var prunes = 0
            private set
        var snapshotReads = 0
            private set

        override suspend fun folderSnapshots(): Map<String, FolderSnapshot> {
            snapshotReads++
            return delegate.folderSnapshots()
        }

        override suspend fun upsertGame(rawGame: RawGame): GameWriteOutcome {
            upserts++
            return delegate.upsertGame(rawGame)
        }

        override suspend fun pruneGames(keptFolderKeys: Set<String>): List<String> {
            prunes++
            return delegate.pruneGames(keptFolderKeys)
        }
    }
}
