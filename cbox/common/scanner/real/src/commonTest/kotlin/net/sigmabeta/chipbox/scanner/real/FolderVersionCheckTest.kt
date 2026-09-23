package net.sigmabeta.chipbox.scanner.real

import net.sigmabeta.chipbox.repository.FolderSnapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FolderVersionCheckTest {

    private val currentReaderVersions = mapOf("psf" to 1, "vgm" to 2)

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
    fun `a format with no dedicated reader compares against zero`() {
        // vgmstream streamed audio persists readerVersion 0; it should still be skippable.
        val snapshot = FolderSnapshot(
            "sig",
            1,
            scannerVersion = 1,
            readerVersions = mapOf("adx" to 0),
        )
        assertTrue(snapshot.isUpToDate(1, ::readerVersionFor))
    }

    @Test
    fun `a pre-versioning snapshot with no reader versions is stale`() {
        val snapshot = FolderSnapshot("sig", 0, scannerVersion = 0, readerVersions = emptyMap())
        assertFalse(snapshot.isUpToDate(1, ::readerVersionFor))
    }
}
