package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReaderParityCommandTest {

    @Test
    fun `parses the DB export format and ignores the trailing title`() {
        // Columns: id, absolutePath, trackNumber, storedLengthMs, extension, title.
        val tsv = """
            1	/music/01 Blue Moon.strm	1	130768	strm	Blue Moon
            2	/music/02 Boss.XA	1	42000	XA	Boss

        """.trimIndent()

        val rows = parseReferenceTsv(tsv)

        assertEquals(
            listOf(
                ParityReferenceRow("/music/01 Blue Moon.strm", 1, 130_768L, "strm"),
                ParityReferenceRow("/music/02 Boss.XA", 1, 42_000L, "xa"),
            ),
            rows,
        )
    }

    @Test
    fun `a reference row with too few columns is rejected with its line number`() {
        assertFailsWith<IllegalArgumentException> {
            parseReferenceTsv("1\t/music/a.strm\t1\t1000")
        }
    }

    @Test
    fun `a non-numeric length is rejected with its line number`() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseReferenceTsv("1\t/music/a.strm\t1\tnot-a-number\tstrm\tTitle")
        }

        assertEquals(true, error.message.orEmpty().contains("line 1"))
    }

    @Test
    fun `counts exact mismatch and unparsed per extension with padding`() {
        val cases = listOf(
            // 1000ms reader + 2000ms padding == stored 3000ms.
            ParityCase("/a.strm", "strm", 1, 3_000L, 1_000L),
            ParityCase("/b.strm", "strm", 1, 3_000L, 999L),
            ParityCase("/c.xa", "xa", 1, 3_000L, null),
        )

        val summary = summarizeParity(cases, paddingMs = 2_000L)

        assertEquals(ParityCounts(exact = 1, mismatch = 1, unparsed = 1), summary.totals)
        assertEquals(ParityCounts(exact = 1, mismatch = 1), summary.perExtension.getValue("strm"))
        assertEquals(ParityCounts(unparsed = 1), summary.perExtension.getValue("xa"))
        assertEquals(listOf(ParityResult.MISMATCH, ParityResult.UNPARSED), summary.problems.map { it.result })
    }

    @Test
    fun `directory mode counts a parsed file separately from exact`() {
        val cases = listOf(
            ParityCase("/a.strm", "strm", 1, expectedLengthMs = null, readerLengthMs = 4_000L),
            ParityCase("/b.strm", "strm", 1, expectedLengthMs = null, readerLengthMs = null),
        )

        val summary = summarizeParity(cases, paddingMs = 2_000L)

        assertEquals(ParityCounts(parsed = 1, unparsed = 1), summary.totals)
    }

    @Test
    fun `runs a reference over synthetic files and reports unparsed without a registered reader`() {
        val corpus = Files.createTempDirectory("perfbench-parity").toFile()
        val file = File(corpus, "01 Takeoff.noreader").apply { writeBytes(ByteArray(64)) }
        val reference = File(Files.createTempDirectory("perfbench-parity-ref").toFile(), "reference.tsv")
        reference.writeText("7\t${file.absolutePath}\t1\t5000\tnoreader\tTakeoff\n")

        val args = ReaderParityArgs(
            corpusDir = corpus,
            referenceFile = reference,
            readerExtension = null,
            paddingMs = 2_000L,
        )

        val summary = runReaderParity(args)

        assertEquals(ParityCounts(unparsed = 1), summary.totals)
        assertEquals("noreader", summary.problems.single().extension)
        assertEquals(file.absolutePath, summary.problems.single().path)
    }

    @Test
    fun `a reference row outside the directory is not run`() {
        val corpus = Files.createTempDirectory("perfbench-parity").toFile()
        val inside = File(corpus, "inside.adx").apply { writeBytes(ByteArray(8)) }
        val outside = Files.createTempFile("perfbench-parity-outside", ".adx").toFile()
        val reference = File(Files.createTempDirectory("perfbench-parity-ref").toFile(), "reference.tsv")
        reference.writeText(
            "1\t${inside.absolutePath}\t1\t5000\tadx\tInside\n" +
                "2\t${outside.absolutePath}\t1\t5000\tadx\tOutside\n",
        )

        val summary = runReaderParity(
            ReaderParityArgs(corpus, reference, readerExtension = null, paddingMs = 2_000L),
        )

        assertEquals(ParityCounts(unparsed = 1), summary.totals)
        assertEquals(inside.absolutePath, summary.problems.single().path)
    }

    @Test
    fun `the reader filter limits the reference rows`() {
        val corpus = Files.createTempDirectory("perfbench-parity").toFile()
        val adx = File(corpus, "a.adx").apply { writeBytes(ByteArray(8)) }
        val xa = File(corpus, "b.xa").apply { writeBytes(ByteArray(8)) }
        val reference = File(Files.createTempDirectory("perfbench-parity-ref").toFile(), "reference.tsv")
        reference.writeText(
            "1\t${adx.absolutePath}\t1\t5000\tadx\tA\n" +
                "2\t${xa.absolutePath}\t1\t5000\txa\tB\n",
        )

        val summary = runReaderParity(
            ReaderParityArgs(corpus, reference, readerExtension = "xa", paddingMs = 2_000L),
        )

        assertEquals(ParityCounts(unparsed = 1), summary.totals)
        assertEquals("xa", summary.perExtension.keys.single())
    }

    @Test
    fun `a directory-only run with an explicit reader includes that extension`() {
        val corpus = Files.createTempDirectory("perfbench-parity").toFile()
        File(corpus, "a.adx").writeBytes(ByteArray(8))
        File(corpus, "nested").mkdirs()
        File(File(corpus, "nested"), "b.xa").writeBytes(ByteArray(8))
        File(corpus, "notes.txt").writeText("not audio")

        val summary = runReaderParity(
            ReaderParityArgs(corpus, referenceFile = null, readerExtension = "xa", paddingMs = 2_000L),
        )

        assertEquals(ParityCounts(unparsed = 1), summary.totals)
        assertEquals(setOf("xa"), summary.perExtension.keys)
        assertEquals(File(File(corpus, "nested"), "b.xa").path, summary.problems.single().path)
    }

    @Test
    fun `a directory-only run without a filter considers only registered readers`() {
        val corpus = Files.createTempDirectory("perfbench-parity").toFile()
        File(corpus, "a.vgm").writeBytes(ByteArray(8))
        File(corpus, "b.noreader").writeBytes(ByteArray(8))
        File(corpus, "notes.txt").writeText("not audio")

        val summary = runReaderParity(
            ReaderParityArgs(corpus, referenceFile = null, readerExtension = null, paddingMs = 2_000L),
        )

        assertEquals(ParityCounts(unparsed = 1), summary.totals)
        assertEquals(setOf("vgm"), summary.perExtension.keys)
    }
}
