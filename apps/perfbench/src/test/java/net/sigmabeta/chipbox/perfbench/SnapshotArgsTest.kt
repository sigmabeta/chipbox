package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SnapshotArgsTest {
    @Test
    fun `requires a corpus directory`() {
        assertFailsWith<IllegalArgumentException> { parseSnapshotOptions(emptyMap()) }
    }

    @Test
    fun `rejects a corpus path that is not a directory`() {
        val file = Files.createTempFile("perfbench-corpus", ".txt").toFile()

        assertFailsWith<IllegalArgumentException> {
            parseSnapshotOptions(mapOf("dir" to file.absolutePath))
        }
    }

    @Test
    fun `defaults the output to the standard snapshot path`() {
        val corpus = Files.createTempDirectory("perfbench-corpus").toFile()

        val args = parseSnapshotOptions(mapOf("dir" to corpus.absolutePath))

        assertEquals(corpus, args.corpusDir)
        assertEquals(File(DEFAULT_SNAPSHOT_OUT), args.outFile)
    }

    @Test
    fun `honours an explicit output path`() {
        val corpus = Files.createTempDirectory("perfbench-corpus").toFile()

        val args = parseSnapshotOptions(mapOf("dir" to corpus.absolutePath, "out" to "some/snapshot.json"))

        assertEquals(File("some/snapshot.json"), args.outFile)
    }
}
