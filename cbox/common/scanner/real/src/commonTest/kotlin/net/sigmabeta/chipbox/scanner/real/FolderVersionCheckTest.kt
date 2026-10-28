package net.sigmabeta.chipbox.scanner.real

import net.sigmabeta.chipbox.repository.FolderSnapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FolderVersionCheckTest {

    private val currentReaderVersions = mapOf("psf" to 1, "vgm" to 2, "adx" to 1)

    private fun readerVersionFor(extension: String): Int = currentReaderVersions[extension] ?: 0

    @Test
    fun `matching scanner and reader versions are up to date`() {
        val snapshot = FolderSnapshot(
            signature = "sig",
            trackCount = 2,
            scannerVersion = 1,
            readerVersions = mapOf("psf" to 1, "vgm" to 2),
        )
        assertTrue(snapshot.isUpToDate(1, ::readerVersionFor))
    }

    @Test
    fun `a stale scanner version forces a re-read`() {
        val snapshot = FolderSnapshot("sig", 1, scannerVersion = 0, readerVersions = mapOf("psf" to 1))
        assertFalse(snapshot.isUpToDate(1, ::readerVersionFor))
    }

    @Test
    fun `a stale reader version for any format forces a re-read`() {
        val snapshot = FolderSnapshot(
            "sig",
            2,
            scannerVersion = 1,
            readerVersions = mapOf("psf" to 1, "vgm" to 1),
        )
        assertFalse(snapshot.isUpToDate(1, ::readerVersionFor))
    }

    @Test
    fun `a streamed format that gained a reader is re-read`() {
        // Cutover: every streamed row was stored at the no-reader sentinel (0) by the native probe.
        // ADX now has a Kotlin reader at version >= 1, so an old snapshot must be re-read.
        val snapshot = FolderSnapshot(
            "sig",
            1,
            scannerVersion = 1,
            readerVersions = mapOf("adx" to 0),
        )
        assertFalse(snapshot.isUpToDate(1, ::readerVersionFor))
    }

    @Test
    fun `a streamed format still without a reader compares against zero`() {
        // No Kotlin reader registered for AT3, so an existing folder holding only such files keeps
        // 0 == 0 and stays skippable — until a reader for it lands (or the scanner version bumps).
        val snapshot = FolderSnapshot(
            "sig",
            1,
            scannerVersion = 1,
            readerVersions = mapOf("at3" to 0),
        )
        assertTrue(snapshot.isUpToDate(1, ::readerVersionFor))
    }

    @Test
    fun `a pre-versioning snapshot with no reader versions is stale`() {
        val snapshot = FolderSnapshot("sig", 0, scannerVersion = 0, readerVersions = emptyMap())
        assertFalse(snapshot.isUpToDate(1, ::readerVersionFor))
    }
}
