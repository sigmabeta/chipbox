package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiffArgsTest {
    @Test
    fun `resolves a folder argument to the results file inside it`() {
        val runA = Files.createTempDirectory("perfbench-run-a").toFile()
        val runB = Files.createTempDirectory("perfbench-run-b").toFile()
        File(runA, "results.json").writeText("{}")
        File(runB, "results.json").writeText("{}")

        val args = parseDiffOptions(mapOf("a" to runA.absolutePath, "b" to runB.absolutePath))

        assertEquals(File(runA, "results.json"), args.baseline)
        assertEquals(File(runB, "results.json"), args.current)
    }

    @Test
    fun `accepts a direct path to a results file`() {
        val file = Files.createTempFile("perfbench-results", ".json").toFile()
        file.writeText("{}")

        val args = parseDiffOptions(mapOf("a" to file.absolutePath, "b" to file.absolutePath))

        assertEquals(file, args.baseline)
        assertEquals(file, args.current)
    }

    @Test
    fun `requires both run arguments`() {
        assertFailsWith<IllegalStateException> { parseDiffOptions(emptyMap()) }
        assertFailsWith<IllegalStateException> { parseDiffOptions(mapOf("a" to "/tmp")) }
    }

    @Test
    fun `rejects a run folder with no results file`() {
        val emptyDir = Files.createTempDirectory("perfbench-empty").toFile()

        assertFailsWith<IllegalArgumentException> {
            parseDiffOptions(mapOf("a" to emptyDir.absolutePath, "b" to emptyDir.absolutePath))
        }
    }
}
