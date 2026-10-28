package net.sigmabeta.chipbox.scanner.real

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.sigmabeta.chipbox.contentsource.LibraryFileInfo
import net.sigmabeta.chipbox.contentsource.LibraryFolderInfo
import net.sigmabeta.chipbox.contentsource.LibraryLocationInfo
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet

/**
 * Cutover behaviour at the scanner's public seam: a file whose extension is a streamed-audio format
 * the vendored vgmstream build knows but no Kotlin reader serves is skipped, counted and named —
 * never silently dropped — while unrelated unknown extensions stay quiet debug-level skips.
 *
 * The scan here is hermetic: the only playable file is a minimal pure-Kotlin PSF, so no native
 * library is involved anywhere.
 */
class RealScannerSkipTest {

    @Test
    fun `a known streamed extension with no reader is counted as skipped`() = runBlocking {
        val folder = "/library/Test Game"
        val files = listOf(
            file(folder, "01 Takeoff.psf", "psf", psfFile(tagText = "title=Takeoff\n")),
            // ATRAC3: in the vendored build's streamed set, outside the Kotlin reader port's scope.
            file(folder, "02 Unreadable.at3", "at3", byteArrayOf(1, 2, 3)),
            // Neither of these is a vgmstream format: skipped silently and *not* counted.
            file(folder, "notes.txt", "txt", "hi".encodeToByteArray()),
            file(folder, "cover.jpg", "jpg", byteArrayOf(0xFF.toByte())),
        )
        val source = FakeLibrarySource(
            folders = listOf(LibraryFolderInfo(folder, files.map { it.info })),
            contents = files.associate { it.info.identifier to it.bytes },
        )
        val hatchet = RecordingHatchet()
        val scanner = RealScanner(
            FakeRepository(emptyMap()),
            source,
            Readers(BluntHatchet()),
            hatchet,
        )

        scanner.startScan()
        val terminal = scanner.state().first { it is ScannerState.Complete || it is ScannerState.Failed }

        val complete = assertIs<ScannerState.Complete>(terminal)
        assertEquals(1, complete.gamesFound)
        assertEquals(1, complete.tracksFound, "only the PSF should become a track")
        assertEquals(0, complete.tracksFailed)
        assertEquals(1, complete.filesSkipped, "only the streamed extension counts as skipped")
        assertEquals(
            "No Kotlin reader for streamed extension 'at3' — skipping 02 Unreadable.at3.",
            hatchet.warnings.first(),
            "the skipped streamed file must be named in a warning",
        )
        assertEquals(
            "1 file(s) skipped: streamed-audio formats this build cannot read (no Kotlin reader, " +
                "or no supported stream). See the per-file warnings above.",
            hatchet.warnings.last(),
            "the scan must end with a skipped-file summary",
        )
    }

    @Test
    fun `a registered reader with no supported stream is counted and reported live`() = runBlocking {
        val folder = "/library/Test Game"
        val files = listOf(
            file(folder, "01 Takeoff.psf", "psf", psfFile(tagText = "title=Takeoff\n")),
            // FSB5 with an external-codec (Vorbis) sample: the registered reader recognises the
            // bank but yields emptyList(), so nothing broke — the stream is just unsupported.
            file(folder, "02 Vorbis.fsb", "fsb", vorbisFsbFile()),
            file(folder, "03 Unreadable.at3", "at3", byteArrayOf(1, 2, 3)),
        )
        val source = FakeLibrarySource(
            folders = listOf(LibraryFolderInfo(folder, files.map { it.info })),
            contents = files.associate { it.info.identifier to it.bytes },
        )
        val hatchet = RecordingHatchet()
        // Hold the post-folder prune until the test has read the live progress. The scanner emits the
        // folder's Scanning state before pruneGames and Complete after it, so gating there makes the
        // live emission observable without racing the replay-1/drop-oldest state stream.
        val pruneGate = CompletableDeferred<Unit>()
        val scanner = RealScanner(
            GatedRepository(FakeRepository(emptyMap()), pruneGate),
            source,
            Readers(BluntHatchet()),
            hatchet,
        )

        scanner.startScan()
        // The scan is parked in pruneGames after emitting the folder's live progress, so the
        // replay-1 state stream is holding that Scanning until the gate opens.
        val live = assertIs<ScannerState.Scanning>(
            withTimeout(10_000) {
                scanner.state().first { it is ScannerState.Scanning && it.filesSkipped == 2 }
            },
        )
        assertEquals(
            2,
            live.filesSkipped,
            "live Scanning progress must carry the running skipped count, not always 0",
        )
        pruneGate.complete(Unit)
        val terminal = scanner.state().first { it is ScannerState.Complete || it is ScannerState.Failed }
        val complete = assertIs<ScannerState.Complete>(terminal)
        assertEquals(2, complete.filesSkipped, "the unsupported FSB5 and the unreadable .at3 both skip")
        assertEquals(
            "Streamed reader found no supported stream in 02 Vorbis.fsb — skipping.",
            hatchet.warnings.first(),
        )
        assertEquals(
            "2 file(s) skipped: streamed-audio formats this build cannot read (no Kotlin reader, " +
                "or no supported stream). See the per-file warnings above.",
            hatchet.warnings.last(),
        )
    }

    /** Minimal FSB5 header with a Vorbis sample: valid structure, undecodable codec. */
    private fun vorbisFsbFile(): ByteArray {
        val bytes = ByteArray(0x60)
        "FSB5".encodeToByteArray().copyInto(bytes, 0)
        bytes.putIntLe(0x04, 0) // version 0
        bytes.putIntLe(0x08, 1) // one subsong
        bytes.putIntLe(0x0C, 0) // sample-header section
        bytes.putIntLe(0x10, 0) // name table
        bytes.putIntLe(0x14, bytes.size - 0x40) // sample data
        bytes.putIntLe(0x18, 0x0F) // FMOD Vorbis
        return bytes
    }

    private fun ByteArray.putIntLe(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value shr 8).toByte()
        this[offset + 2] = (value shr 16).toByte()
        this[offset + 3] = (value shr 24).toByte()
    }

    /** The fake repository, with pruneGames held open so the test can observe live progress. */
    private class GatedRepository(
        private val delegate: Repository,
        private val pruneGate: CompletableDeferred<Unit>,
    ) : Repository by delegate {
        override suspend fun pruneGames(keptFolderKeys: Set<String>): List<String> {
            pruneGate.await()
            return delegate.pruneGames(keptFolderKeys)
        }
    }

    /** Captures the user-facing reporting surface: warnings name what vanished, info is noise. */
    private class RecordingHatchet : Hatchet {
        private val captured = mutableListOf<String>()
        val warnings: List<String> get() = synchronized(captured) { captured.toList() }

        override fun v(message: String) = Unit
        override fun d(message: String) = Unit
        override fun i(message: String) = Unit
        override fun w(message: String) {
            synchronized(captured) { captured += message }
        }
        override fun e(message: String) {
            synchronized(captured) { captured += "ERROR: $message" }
        }
        override fun log(severity: Int, message: String) = Unit
    }

    private data class TestFile(val info: LibraryFileInfo, val bytes: ByteArray)

    private fun file(folder: String, name: String, extension: String, bytes: ByteArray): TestFile {
        val identifier = "$folder/$name"
        return TestFile(
            LibraryFileInfo(
                identifier = identifier,
                parentFolderId = folder,
                name = name,
                extension = extension,
                mimeType = null,
                sizeBytes = bytes.size.toLong(),
                lastModifiedMs = 0L,
            ),
            bytes,
        )
    }

    /** Minimal PSF header + `[TAG]` block, as a reader accepts it. */
    private fun psfFile(tagText: String): ByteArray {
        val tagBytes = "[TAG]".encodeToByteArray() + tagText.encodeToByteArray()
        val out = ByteArray(16 + tagBytes.size)
        "PSF".encodeToByteArray().copyInto(out, 0)
        out[3] = 0x01 // PSX
        tagBytes.copyInto(out, 16)
        return out
    }

    private class FakeLibrarySource(
        private val folders: List<LibraryFolderInfo>,
        private val contents: Map<String, ByteArray>,
    ) : LibrarySource {
        override val sourceId: String = "test"

        override val locations = MutableStateFlow(
            listOf(LibraryLocationInfo("/library", "Library")),
        )

        override fun scanFolders(): Flow<LibraryFolderInfo> = folders.asFlow()

        override suspend fun openBytes(identifier: String): ByteArray? = contents[identifier]

        override fun addLibraryLocation(identifier: String) = Unit

        override fun removeLibraryLocation(identifier: String) = Unit
    }
}
