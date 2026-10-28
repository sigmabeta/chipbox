package net.sigmabeta.chipbox.server.scanner

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.server.testing.RarEntry
import net.sigmabeta.chipbox.server.testing.allTracks
import net.sigmabeta.chipbox.server.testing.rar4
import net.sigmabeta.chipbox.server.testing.scanCorpus
import net.sigmabeta.chipbox.server.testing.sha256Hex
import net.sigmabeta.chipbox.server.testing.spcFile
import net.sigmabeta.chipbox.server.testing.tempDir
import net.sigmabeta.sage.logging.BluntHatchet

class RsnExtractionTest {

    @Test
    fun `scan extracts RSN members as ordinary SPC tracks`() {
        val corpus = tempDir("rsn-corpus")
        val workDir = tempDir("rsn-work")
        val first = spcFile(title = "First")
        val second = spcFile(title = "Second")
        val archiveBytes = rar4(
            // Deliberately not in sorted order: extraction must follow rsnSpcMembers' order.
            RarEntry("b/02 Second.spc", second),
            RarEntry("a/01 First.spc", first),
            RarEntry("notes.txt", "not audio".encodeToByteArray()),
        )
        File(corpus, "tiny.rsn").writeBytes(archiveBytes)

        val result = scanCorpus(corpus, workDir, ServerRsnExtractor(workDir, BluntHatchet()))

        val complete = assertIs<ScannerState.Complete>(result.state)
        assertEquals(2, complete.tracksFound)
        assertEquals(0, complete.tracksFailed)

        val tracks = result.repository.allTracks()
        assertEquals(setOf("First", "Second"), tracks.map { it.title }.toSet())
        val extractionDir = File(workDir, "rsn/${archiveBytes.sha256Hex()}")
        tracks.forEach { track ->
            assertEquals("spc", track.extension)
            assertEquals(0, track.trackNumber)
            assertTrue(
                track.path.startsWith(extractionDir.absolutePath + File.separator),
                "expected ${track.path} under ${extractionDir.absolutePath}",
            )
            assertTrue(track.path.endsWith(".spc"), "expected an .spc path, got ${track.path}")
        }

        val firstTrack = tracks.single { it.title == "First" }
        val secondTrack = tracks.single { it.title == "Second" }
        assertEquals("01 First.spc", firstTrack.path.substringAfterLast('/'))
        assertEquals("02 Second.spc", secondTrack.path.substringAfterLast('/'))
        assertTrue(File(firstTrack.path).readBytes().contentEquals(first))
        assertTrue(File(secondTrack.path).readBytes().contentEquals(second))
    }

    @Test
    fun `scanning without an extractor keeps archive staging tracks`() {
        // Desktop, Android, and CLI construct RealScanner without an extractor: RSN must keep
        // pointing at the archive with its member index so playback can stage the member itself.
        val corpus = tempDir("rsn-corpus")
        val workDir = tempDir("rsn-work")
        val archive = File(corpus, "tiny.rsn")
        archive.writeBytes(
            rar4(
                RarEntry("02 Second.spc", spcFile(title = "Second")),
                RarEntry("01 First.spc", spcFile(title = "First")),
            ),
        )

        val result = scanCorpus(corpus, workDir, extractor = null)
        assertIs<ScannerState.Complete>(result.state)
        val tracks = result.repository.allTracks().sortedBy { it.trackNumber }
        assertEquals(listOf(0, 1), tracks.map { it.trackNumber })
        assertTrue(tracks.all { it.extension == "rsn" })
        assertTrue(tracks.all { it.path == archive.absolutePath })
        assertFalse(File(workDir, "rsn").exists(), "nothing should be extracted")
    }

    @Test
    fun `extraction is deterministic and reuses matching output`() {
        val corpus = tempDir("rsn-corpus")
        val workDir = tempDir("rsn-work")
        val first = spcFile(title = "First")
        val second = spcFile(title = "Second")
        val archiveBytes = rar4(
            RarEntry("02 Second.spc", second),
            RarEntry("01 First.spc", first),
        )
        File(corpus, "tiny.rsn").writeBytes(archiveBytes)
        val extractor = ServerRsnExtractor(workDir, BluntHatchet())

        val firstScan = scanCorpus(corpus, workDir, extractor).repository.allTracks()
        val extractionDir = File(workDir, "rsn/${archiveBytes.sha256Hex()}")
        assertTrue(extractionDir.isDirectory)

        // A second scan of the same archive into a fresh repository maps to identical paths.
        val secondScan = scanCorpus(corpus, workDir, extractor).repository.allTracks()
        assertEquals(
            firstScan.associate { it.title to it.path },
            secondScan.associate { it.title to it.path },
        )

        // Output is content-addressed, so a wrong-sized/mismatched file is rewritten in place.
        val firstPath = File(firstScan.single { it.title == "First" }.path)
        firstPath.writeBytes(byteArrayOf(1, 2, 3))
        scanCorpus(corpus, workDir, extractor)
        assertTrue(firstPath.readBytes().contentEquals(first))
    }

    @Test
    fun `m3u overlay still matches the correct member after extraction`() {
        val corpus = tempDir("rsn-corpus")
        val workDir = tempDir("rsn-work")
        val archiveBytes = rar4(
            RarEntry("02 Second.spc", spcFile(title = "Second")),
            RarEntry("01 First.spc", spcFile(title = "First")),
        )
        File(corpus, "tiny.rsn").writeBytes(archiveBytes)
        File(corpus, "tiny.m3u").writeText(
            """
            tiny.rsn::SPC,1,Overlay One,4:00,0:00,0:06
            tiny.rsn::SPC,2,Overlay Two,5:00,0:00,0:06
            """.trimIndent(),
        )

        val result = scanCorpus(corpus, workDir, ServerRsnExtractor(workDir, BluntHatchet()))
        assertIs<ScannerState.Complete>(result.state)

        val tracks = result.repository.allTracks().associateBy { it.title }
        // The overlay sets titles by (archive basename, 1-based member index); each one must land
        // on the extracted file for that same member, with track numbers normalised to 0.
        assertEquals("01 First.spc", tracks.getValue("Overlay One").path.substringAfterLast('/'))
        assertEquals("02 Second.spc", tracks.getValue("Overlay Two").path.substringAfterLast('/'))
        assertEquals(4 * 60_000L, tracks.getValue("Overlay One").trackLengthMs)
        assertEquals(5 * 60_000L, tracks.getValue("Overlay Two").trackLengthMs)
        assertTrue(tracks.values.all { it.trackNumber == 0 && it.extension == "spc" })
    }
}
