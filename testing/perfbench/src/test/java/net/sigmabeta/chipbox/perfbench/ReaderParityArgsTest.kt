package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReaderParityArgsTest {
    @Test
    fun `requires a directory or a reference`() {
        assertFailsWith<IllegalArgumentException> { parseReaderParityOptions(emptyMap()) }
    }

    @Test
    fun `rejects a directory that is not a directory`() {
        val file = Files.createTempFile("perfbench-parity", ".tsv").toFile()

        assertFailsWith<IllegalArgumentException> {
            parseReaderParityOptions(mapOf("dir" to file.absolutePath))
        }
    }

    @Test
    fun `rejects a reference that does not exist`() {
        assertFailsWith<IllegalArgumentException> {
            parseReaderParityOptions(mapOf("reference" to "/does/not/exist.tsv"))
        }
    }

    @Test
    fun `accepts a reference on its own`() {
        val reference = Files.createTempFile("perfbench-parity", ".tsv").toFile()

        val args = parseReaderParityOptions(mapOf("reference" to reference.absolutePath))

        assertNull(args.corpusDir)
        assertEquals(reference, args.referenceFile)
        assertEquals(DEFAULT_PARITY_PADDING_MS, args.paddingMs)
        assertNull(args.readerExtension)
    }

    @Test
    fun `defaults padding to the scanner zero-fade padding`() {
        val corpus = Files.createTempDirectory("perfbench-parity").toFile()

        val args = parseReaderParityOptions(mapOf("dir" to corpus.absolutePath))

        assertEquals(2_000L, args.paddingMs)
    }

    @Test
    fun `disables padding when asked`() {
        val corpus = Files.createTempDirectory("perfbench-parity").toFile()

        val args = parseReaderParityOptions(
            mapOf("dir" to corpus.absolutePath, "no-padding" to "true"),
        )

        assertEquals(0L, args.paddingMs)
    }

    @Test
    fun `normalizes the reader extension and trims the dot`() {
        val corpus = Files.createTempDirectory("perfbench-parity").toFile()

        val args = parseReaderParityOptions(mapOf("dir" to corpus.absolutePath, "reader" to ".XA"))

        assertEquals("xa", args.readerExtension)
    }
}
