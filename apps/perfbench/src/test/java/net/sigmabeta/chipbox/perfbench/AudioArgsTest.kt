package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AudioArgsTest {
    private val corpus: File = Files.createTempDirectory("perfbench-corpus").toFile()

    @Test
    fun `applies defaults`() {
        val args = parseAudioOptions(mapOf("dir" to corpus.absolutePath))

        assertNull(args.filter)
        assertEquals(3, args.repeat)
        assertEquals(30, args.sampleSeconds)
        assertEquals(File("build/perfbench"), args.outDir)
    }

    @Test
    fun `parses filter and numeric flags`() {
        val args = parseAudioOptions(
            mapOf(
                "dir" to corpus.absolutePath,
                "filter" to "Chrono Trigger/**",
                "repeat" to "5",
                "seconds" to "10",
                "out" to "/tmp/perfbench-out",
            ),
        )

        assertEquals("Chrono Trigger/**", args.filter)
        assertEquals(5, args.repeat)
        assertEquals(10, args.sampleSeconds)
        assertEquals(File("/tmp/perfbench-out"), args.outDir)
    }

    @Test
    fun `requires a corpus directory`() {
        assertFailsWith<IllegalArgumentException> { parseAudioOptions(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { parseAudioOptions(mapOf("dir" to "/no/such/corpus")) }
    }

    @Test
    fun `rejects a missing corpus directory with the same message as snapshot`() {
        val audio = assertFailsWith<IllegalArgumentException> { parseAudioOptions(emptyMap()) }
        val snapshot = assertFailsWith<IllegalArgumentException> { parseSnapshotOptions(emptyMap()) }

        assertEquals(audio.message, snapshot.message)
    }

    @Test
    fun `rejects a non-directory corpus with the same message as snapshot`() {
        val file = Files.createTempFile("perfbench-corpus", ".txt").toFile()

        val audio = assertFailsWith<IllegalArgumentException> {
            parseAudioOptions(mapOf("dir" to file.absolutePath))
        }
        val snapshot = assertFailsWith<IllegalArgumentException> {
            parseSnapshotOptions(mapOf("dir" to file.absolutePath))
        }

        assertEquals(audio.message, snapshot.message)
    }

    @Test
    fun `rejects a malformed filter glob`() {
        assertFailsWith<IllegalArgumentException> {
            parseAudioOptions(mapOf("dir" to corpus.absolutePath, "filter" to "["))
        }
    }

    @Test
    fun `rejects non-positive repeat and sample seconds`() {
        assertFailsWith<IllegalArgumentException> {
            parseAudioOptions(mapOf("dir" to corpus.absolutePath, "repeat" to "0"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseAudioOptions(mapOf("dir" to corpus.absolutePath, "seconds" to "0"))
        }
    }

    @Test
    fun `rejects unparseable numeric flags instead of defaulting`() {
        assertFailsWith<IllegalStateException> {
            parseAudioOptions(mapOf("dir" to corpus.absolutePath, "repeat" to "abc"))
        }
        assertFailsWith<IllegalStateException> {
            parseAudioOptions(mapOf("dir" to corpus.absolutePath, "seconds" to "ten"))
        }
    }
}
