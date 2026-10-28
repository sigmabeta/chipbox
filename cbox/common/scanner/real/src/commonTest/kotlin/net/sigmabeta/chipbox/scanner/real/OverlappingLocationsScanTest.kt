package net.sigmabeta.chipbox.scanner.real

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.contentsource.LibraryLocationInfo
import net.sigmabeta.chipbox.contentsource.fake.FakeLibrarySource
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.FolderSnapshot
import net.sigmabeta.chipbox.repository.GameWriteOutcome
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.scanner.state.ScanFailureKind
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.Hatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Overlapping library roots are a configuration error: the shared folders would be emitted twice
 * in one scan and the parallel readers would race on the unique `game.folder_key` index. The
 * scanner must refuse the configuration before it walks, writes, or prunes — so the stored library
 * survives intact — and the terminal state must not carry a host path.
 */
class OverlappingLocationsScanTest {

    @Test
    fun `a nested root fails the scan before any write or prune`() = runTest {
        val source = FakeLibrarySource(
            initial = listOf(
                LibraryLocationInfo("/library", "library"),
                LibraryLocationInfo("/library/SNES", "SNES"),
            ),
        )
        val repository = RecordingRepository()
        val hatchet = RecordingHatchet()

        val state = scan(source, repository, hatchet)

        val failure = assertIs<ScannerState.Failed>(state)
        assertEquals(ScanFailureKind.CONFIGURATION, failure.kind)
        assertEquals(0, repository.upserts, "nothing may be written for a refused configuration")
        assertEquals(0, repository.prunes, "the stored library must not be reconciled away")
        assertEquals(0, repository.snapshotReads, "refuse before touching the library")

        // The log names both configured roots in full so the admin can fix the list.
        val refusal = hatchet.errors.single()
        assertTrue(refusal.contains("/library/SNES"), refusal)
        assertTrue(refusal.contains("/library"), refusal)

        // The state the health endpoint exposes must not echo a host path.
        assertFalse(state.path.contains('/'), "the failure message is not a path: ${state.path}")
        assertTrue(state.path.contains("overlap", ignoreCase = true), state.path)
    }

    @Test
    fun `the same root configured twice fails the scan`() = runTest {
        val source = FakeLibrarySource(
            initial = listOf(
                LibraryLocationInfo("/library", "library"),
                LibraryLocationInfo("/library", "library"),
            ),
        )
        val repository = RecordingRepository()

        assertIs<ScannerState.Failed>(scan(source, repository, RecordingHatchet()))
        assertEquals(0, repository.upserts)
        assertEquals(0, repository.prunes)
    }

    @Test
    fun `disjoint roots are not refused`() = runTest {
        val source = FakeLibrarySource(
            initial = listOf(
                LibraryLocationInfo("/library/SNES", "SNES"),
                LibraryLocationInfo("/library/Genesis", "Genesis"),
            ),
        )
        val repository = RecordingRepository()

        assertIs<ScannerState.Complete>(scan(source, repository, RecordingHatchet()))
        assertEquals(0, repository.upserts, "the fake source exposes no folders to read")
        assertEquals(1, repository.prunes, "a valid configuration still reconciles the library")
    }

    @Test
    fun `a single root is not refused`() = runTest {
        val source = FakeLibrarySource(
            initial = listOf(LibraryLocationInfo("/library", "library")),
        )

        assertIs<ScannerState.Complete>(scan(source, RecordingRepository(), RecordingHatchet()))
        Unit
    }

    private suspend fun scan(
        source: FakeLibrarySource,
        repository: Repository,
        hatchet: Hatchet,
    ): ScannerState {
        val scanner = RealScanner(
            repository = repository,
            librarySource = source,
            readers = Readers(hatchet),
            hatchet = hatchet,
        )
        scanner.startScan()
        return scanner.state().first { it is ScannerState.Complete || it is ScannerState.Failed }
    }
}

/**
 * Delegates every [Repository] call to an inert [FakeRepository], counting the writes a refused
 * scan must never make.
 */
private class RecordingRepository : Repository by FakeRepository(emptyMap()) {
    var upserts = 0
        private set
    var prunes = 0
        private set
    var snapshotReads = 0
        private set

    override suspend fun folderSnapshots(): Map<String, FolderSnapshot> {
        snapshotReads++
        return emptyMap()
    }

    override suspend fun upsertGame(rawGame: RawGame): GameWriteOutcome {
        upserts++
        return GameWriteOutcome(0L, net.sigmabeta.chipbox.repository.GameWriteResult.UNCHANGED)
    }

    override suspend fun pruneGames(keptFolderKeys: Set<String>): List<String> {
        prunes++
        return emptyList()
    }
}

/** Records error-level log lines so the test can assert the refusal names both roots. */
private class RecordingHatchet : Hatchet {
    val errors: MutableList<String> = mutableListOf()

    override fun v(message: String) = Unit
    override fun d(message: String) = Unit
    override fun i(message: String) = Unit
    override fun w(message: String) = Unit
    override fun e(message: String) {
        errors += message
    }

    override fun log(severity: Int, message: String) {
        if (severity >= ERROR_SEVERITY) errors += message
    }

    private companion object {
        const val ERROR_SEVERITY = 6
    }
}
