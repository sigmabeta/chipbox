package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CorpusManifestTest {

    @Test
    fun `lists measured files sorted by path relative to the corpus root with known-good digests and sizes`() {
        val root = tempDir()
        val top = write(root, "top.vgm", "chipbox perfbench corpus top-level\n")
        val nested = write(root, "nested/sub.vgm", "chipbox perfbench corpus nested\n")

        val manifest = corpusManifest(root, listOf(top, nested))

        assertEquals(
            listOf(
                CorpusEntry(
                    path = "nested/sub.vgm",
                    sha256 = "afd602630586a4cea55676751a6310ffa7bb957070edd1b4e317e857c60c6148",
                    bytes = 32,
                ),
                CorpusEntry(
                    path = "top.vgm",
                    sha256 = "d71619367a9e5e5b3645a024560b621e1526278aea3a18be740ba29cfaf49ad0",
                    bytes = 35,
                ),
            ),
            manifest,
        )
    }

    @Test
    fun `two corpora with the same files produce equal manifests`() {
        val host = tempDir()
        val device = tempDir()
        val hostFiles = listOf(
            write(host, "a.vgm", "chipbox perfbench corpus top-level\n"),
            write(host, "nested/b.vgm", "chipbox perfbench corpus nested\n"),
        )
        val deviceFiles = listOf(
            write(device, "a.vgm", "chipbox perfbench corpus top-level\n"),
            write(device, "nested/b.vgm", "chipbox perfbench corpus nested\n"),
        )

        assertEquals(corpusManifest(host, hostFiles), corpusManifest(device, deviceFiles))
    }

    @Test
    fun `manifests with differing file content are not equal`() {
        val a = tempDir()
        val b = tempDir()
        val sameSizeA = write(a, "same.vgm", "AAAA\n")
        val sameSizeB = write(b, "same.vgm", "BBBB\n")

        assertNotEquals(corpusManifest(a, listOf(sameSizeA)), corpusManifest(b, listOf(sameSizeB)))
    }

    @Test
    fun `manifests with differing file sizes are not equal`() {
        val a = tempDir()
        val b = tempDir()
        val short = write(a, "same.vgm", "AAAA\n")
        val long = write(b, "same.vgm", "AAAABBBBCCCC\n")

        assertNotEquals(corpusManifest(a, listOf(short)), corpusManifest(b, listOf(long)))
    }

    @Test
    fun `manifests with the same entries in differing order are not equal`() {
        val root = tempDir()
        val files = listOf(
            write(root, "a.vgm", "chipbox perfbench corpus top-level\n"),
            write(root, "b.vgm", "chipbox perfbench corpus nested\n"),
        )

        val manifest = corpusManifest(root, files)

        assertNotEquals(manifest, manifest.reversed())
    }

    private fun tempDir(): File = Files.createTempDirectory("perfbench-corpus").toFile()

    private fun write(root: File, relativePath: String, content: String): File {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }
}
