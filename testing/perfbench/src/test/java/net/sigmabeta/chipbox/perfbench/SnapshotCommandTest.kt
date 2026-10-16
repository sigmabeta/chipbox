package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamProber
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamSubsong
import net.sigmabeta.chipbox.repository.LibrarySnapshot
import net.sigmabeta.chipbox.repository.LibrarySnapshotJson

/**
 * End-to-end generation: the `snapshot` command drives the real host scanner over a small sample
 * corpus and writes a portable snapshot. The sample uses a minimal pure-Kotlin PSF, so the scan
 * needs no native libraries; the injected prober is the no-op that keeps vgmstream out of the test.
 */
class SnapshotCommandTest {
    @Test
    fun `scans the corpus and writes a snapshot with corpus-relative paths`() {
        val corpus = Files.createTempDirectory("perfbench-corpus").toFile()
        val gameDir = File(corpus, "PilotWings").apply { mkdirs() }
        File(gameDir, "lib.psflib").writeBytes(psfFile(tagText = ""))
        File(gameDir, "01 Takeoff.psf").writeBytes(
            psfFile(tagText = "_lib=lib.psflib\ntitle=Takeoff\ngame=PilotWings\n"),
        )
        val outFile = File(Files.createTempDirectory("perfbench-out").toFile(), "library-snapshot.json")

        val written = runSnapshot(SnapshotArgs(corpus, outFile), prober = NoVgmstreamProber)

        assertTrue(outFile.isFile, "the snapshot file should be written")
        val snapshot = LibrarySnapshotJson.decodeFromString<LibrarySnapshot>(outFile.readText())
        assertEquals(written, snapshot)
        assertEquals(corpus.invariantSeparatorsPath, snapshot.root)

        val game = snapshot.games.single()
        assertEquals("PilotWings", game.title)
        assertEquals("PilotWings", game.folderKey)
        val track = game.tracks.single()
        assertEquals("PilotWings/01 Takeoff.psf", track.path)
        assertEquals("Takeoff", track.title)
        assertEquals("PilotWings/lib.psflib", track.chainFiles.single().uri)
        assertTrue(track.scannerVersion >= 1, "the scanner version should be stamped on the track")
        assertEquals(1, snapshot.readerVersions["psf"])
    }

    @Test
    fun `records the scanner version in the snapshot metadata`() {
        val corpus = Files.createTempDirectory("perfbench-corpus").toFile()
        File(corpus, "Game").apply { mkdirs() }
        File(File(corpus, "Game"), "track.psf").writeBytes(psfFile(tagText = "title=Track\n"))
        val outFile = File(Files.createTempDirectory("perfbench-out").toFile(), "library-snapshot.json")

        val snapshot = runSnapshot(SnapshotArgs(corpus, outFile), prober = NoVgmstreamProber)

        assertTrue(snapshot.scannerVersion >= 1)
        assertEquals(LibrarySnapshot.FORMAT_VERSION, snapshot.formatVersion)
    }

    private fun psfFile(tagText: String): ByteArray {
        val tagBytes = "[TAG]".encodeToByteArray() + tagText.encodeToByteArray()
        val out = ByteArray(16 + tagBytes.size)
        "PSF".encodeToByteArray().copyInto(out, 0)
        out[3] = 0x01 // PSX
        tagBytes.copyInto(out, 16)
        return out
    }

    /** No-op prober: the sample corpus is pure-Kotlin PSF, so vgmstream must never need to load. */
    private object NoVgmstreamProber : VgmstreamProber {
        override fun isSupported(extension: String): Boolean = false

        override fun probe(bytes: ByteArray, extension: String): List<VgmstreamSubsong> = emptyList()
    }
}
