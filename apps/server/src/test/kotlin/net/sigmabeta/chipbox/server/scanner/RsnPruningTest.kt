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
import net.sigmabeta.chipbox.server.testing.newRepository
import net.sigmabeta.chipbox.server.testing.rar4
import net.sigmabeta.chipbox.server.testing.scanCorpus
import net.sigmabeta.chipbox.server.testing.sha256Hex
import net.sigmabeta.chipbox.server.testing.spcFile
import net.sigmabeta.chipbox.server.testing.tempDir
import net.sigmabeta.sage.logging.BluntHatchet

class RsnPruningTest {

    @Test
    fun `successful scan prunes orphan directories and keeps output of skipped folders`() {
        val corpus = tempDir("rsn-corpus")
        val workDir = tempDir("rsn-work")
        val archiveBytes = rar4(
            RarEntry("01 First.spc", spcFile(title = "First")),
            RarEntry("02 Second.spc", spcFile(title = "Second")),
        )
        File(corpus, "tiny.rsn").writeBytes(archiveBytes)
        val repository = newRepository()
        val extractor = ServerRsnExtractor(workDir, BluntHatchet())

        val firstScan = scanCorpus(corpus, workDir, extractor, repository)
        assertIs<ScannerState.Complete>(firstScan.state)
        val referencedDir = File(workDir, "rsn/${archiveBytes.sha256Hex()}")
        assertTrue(referencedDir.isDirectory)

        val orphanDir = File(workDir, "rsn/${"ab".repeat(32)}").apply { mkdirs() }
        val orphanMember = File(orphanDir, "orphan.spc").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        // The second scan sees an unchanged folder and skips it entirely, so extraction never runs.
        // Pruning must consult the surviving library (not "what this scan extracted") to keep the
        // skipped folder's content-addressed output.
        val secondScan = scanCorpus(corpus, workDir, extractor, repository)
        assertIs<ScannerState.Complete>(secondScan.state)
        assertTrue(referencedDir.isDirectory)
        assertTrue(File(referencedDir, "01 First.spc").isFile)
        assertFalse(orphanDir.exists(), "orphaned extraction output should be pruned")
        assertFalse(orphanMember.exists())
    }

    @Test
    fun `failed scan never deletes orphaned extraction output`() {
        val corpus = tempDir("rsn-corpus")
        val workDir = tempDir("rsn-work")
        val archiveBytes = rar4(RarEntry("01 First.spc", spcFile(title = "First")))
        File(corpus, "tiny.rsn").writeBytes(archiveBytes)
        val repository = newRepository()
        val extractor = ServerRsnExtractor(workDir, BluntHatchet())

        assertIs<ScannerState.Complete>(scanCorpus(corpus, workDir, extractor, repository).state)
        val orphanDir = File(workDir, "rsn/${"cd".repeat(32)}").apply { mkdirs() }
        File(orphanDir, "orphan.spc").writeBytes(byteArrayOf(4, 5, 6))

        // An unreadable SPC makes the scan partial (tracksFailed > 0) while the RSN still extracts.
        File(corpus, "broken.spc").writeBytes("definitely not an SPC".encodeToByteArray())
        val failedScan = scanCorpus(corpus, workDir, extractor, repository)
        val complete = assertIs<ScannerState.Complete>(failedScan.state)
        assertTrue(complete.tracksFailed > 0, "the broken SPC should be reported as a failure")
        assertTrue(orphanDir.isDirectory, "a partial scan must never prune extraction output")
        assertTrue(failedScan.repository.allTracks().any { it.title == "First" })
    }

    @Test
    fun `output names are deterministic, traversal-free, and collision-free`() {
        assertEquals(
            listOf("00-01 Theme.spc", "01-01 Theme.spc", "02 Other.spc"),
            ServerRsnExtractor.memberFileNames(
                listOf("disc1/01 Theme.spc", "disc2/01 Theme.spc", "02 Other.spc"),
            ),
        )
        assertEquals(
            listOf("evil.spc"),
            ServerRsnExtractor.memberFileNames(listOf("../../evil.spc")),
        )
        assertEquals(
            listOf("track-0.spc"),
            ServerRsnExtractor.memberFileNames(listOf("..")),
        )
    }
}
