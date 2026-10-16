package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CorpusPinTest {
    @Test
    fun `orders manifest entries by path relative to the root with slash separators`() {
        val root = tempDir()
        val nested = writeFile(File(root, "Game"), "track.usf", "abc")
        val topLevel = writeFile(root, "aaa.usf", "abc")

        val manifest = corpusManifest(root, listOf(nested, topLevel))

        assertEquals(listOf("Game/track.usf", "aaa.usf"), manifest.map { it.path })
    }

    @Test
    fun `hashes each corpus file and records its size`() {
        val root = tempDir()
        val track = writeFile(File(root, "Game"), "track.usf", "abc")

        val entry = corpusManifest(root, listOf(track)).single()

        assertEquals("Game/track.usf", entry.path)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", entry.sha256)
        assertEquals(3L, entry.bytes)
    }

    @Test
    fun `pins a corpus with the manifest of its pushed files and the snapshot hash`() {
        val corpusRoot = tempDir()
        writeFile(File(corpusRoot, "Game"), "track.usf", "abc")
        val snapshot = writeFile(tempDir(), "library-snapshot.json", "abc")

        val pin = corpusPin(corpusRoot, snapshot)

        assertEquals(corpusRoot.absolutePath, pin.root)
        assertEquals(listOf("Game/track.usf"), pin.manifest.map { it.path })
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", pin.snapshotHash)
    }

    @Test
    fun `refuses to pin a corpus root that is not a directory`() {
        val missing = File(tempDir(), "not-pushed")
        val snapshot = writeFile(tempDir(), "library-snapshot.json", "abc")

        assertFailsWith<IllegalArgumentException> { corpusPin(missing, snapshot) }
    }

    @Test
    fun `refuses to pin a run that has no snapshot`() {
        val corpusRoot = tempDir()
        writeFile(File(corpusRoot, "Game"), "track.usf", "abc")
        val snapshot = File(tempDir(), "library-snapshot.json")

        assertFailsWith<IllegalArgumentException> { corpusPin(corpusRoot, snapshot) }
    }

    private fun tempDir(): File =
        Files.createTempDirectory("perfbench-pin").toFile().apply { deleteOnExit() }

    private fun writeFile(dir: File, name: String, content: String): File {
        dir.mkdirs()
        return File(dir, name).apply {
            writeText(content)
            deleteOnExit()
        }
    }
}
