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

        assertNull(args.only)
        assertEquals(3, args.repeat)
        assertEquals(30, args.sampleSeconds)
        assertEquals(File("build/perfbench"), args.outDir)
    }

    @Test
    fun `parses only and numeric flags`() {
        val args = parseAudioOptions(
            mapOf(
                "dir" to corpus.absolutePath,
                "only" to "audio.usf, audio.psf",
                "repeat" to "5",
                "seconds" to "10",
                "out" to "/tmp/perfbench-out",
            ),
        )

        assertEquals(listOf(AudioScenario.USF, AudioScenario.PSF), args.only)
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
    fun `rejects unknown scenario ids`() {
        assertFailsWith<IllegalStateException> {
            parseAudioOptions(mapOf("dir" to corpus.absolutePath, "only" to "audio.spc"))
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
