package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BaselineArgsTest {
    @Test
    fun `splits the recordings in order and keeps the output path`() {
        val a = tempFile()
        val b = tempFile()

        val args = parseBaselineOptions(mapOf("runs" to "${a.path}, ${b.path}", "out" to "out.json"))

        assertEquals(listOf(a, b), args.recordings)
        assertEquals(File("out.json"), args.outFile)
    }

    @Test
    fun `rejects a missing recording`() {
        assertFailsWith<IllegalArgumentException> {
            parseBaselineOptions(mapOf("runs" to "/no/such/results.json", "out" to "out.json"))
        }
    }

    @Test
    fun `requires both runs and out`() {
        assertFailsWith<IllegalStateException> { parseBaselineOptions(mapOf("out" to "out.json")) }
        assertFailsWith<IllegalStateException> { parseBaselineOptions(mapOf("runs" to tempFile().path)) }
    }

    @Test
    fun `formats the spread relative to the median`() {
        assertEquals("7.00 / 8.00 / 9.00 (25.0%)", spreadLine(listOf(9.0, 7.0, 8.0)))
    }

    private fun tempFile(): File =
        Files.createTempFile("perfbench-baseline", ".json").toFile().apply { deleteOnExit() }
}
