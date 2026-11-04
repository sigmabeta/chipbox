package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CacheArgsTest {
    private val corpus: File = Files.createTempDirectory("perfbench-cache-corpus").toFile()

    @Test
    fun `applies defaults`() {
        val args = parseCacheOptions(mapOf("dir" to corpus.absolutePath))

        assertNull(args.filter)
        assertEquals(3, args.repeat)
        assertEquals(30, args.sampleSeconds)
        assertEquals(File("build/perfbench/cache"), args.outDir)
        assertEquals(false, args.uncached)
    }

    @Test
    fun `bare --uncached selects the cache-bypass path`() {
        val args = parseCacheOptions(
            mapOf("dir" to corpus.absolutePath, "uncached" to "true"),
        )

        assertEquals(true, args.uncached)
    }

    @Test
    fun `parses filter and numeric flags`() {
        val args = parseCacheOptions(
            mapOf(
                "dir" to corpus.absolutePath,
                "filter" to "Pokemon Heart Gold & Soul Silver/**",
                "repeat" to "5",
                "seconds" to "10",
                "out" to "/tmp/perfbench-cache",
            ),
        )

        assertEquals("Pokemon Heart Gold & Soul Silver/**", args.filter)
        assertEquals(5, args.repeat)
        assertEquals(10, args.sampleSeconds)
        assertEquals(File("/tmp/perfbench-cache"), args.outDir)
    }

    @Test
    fun `requires a corpus directory`() {
        assertFailsWith<IllegalArgumentException> { parseCacheOptions(emptyMap()) }
        assertFailsWith<IllegalArgumentException> {
            parseCacheOptions(mapOf("dir" to "/no/such/corpus"))
        }
    }

    @Test
    fun `rejects a malformed filter glob`() {
        assertFailsWith<IllegalArgumentException> {
            parseCacheOptions(mapOf("dir" to corpus.absolutePath, "filter" to "["))
        }
    }

    @Test
    fun `rejects non-positive repeat and sample seconds`() {
        assertFailsWith<IllegalArgumentException> {
            parseCacheOptions(mapOf("dir" to corpus.absolutePath, "repeat" to "0"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseCacheOptions(mapOf("dir" to corpus.absolutePath, "seconds" to "0"))
        }
    }

    @Test
    fun `rejects unparseable numeric flags instead of defaulting`() {
        assertFailsWith<IllegalStateException> {
            parseCacheOptions(mapOf("dir" to corpus.absolutePath, "repeat" to "abc"))
        }
        assertFailsWith<IllegalStateException> {
            parseCacheOptions(mapOf("dir" to corpus.absolutePath, "seconds" to "ten"))
        }
    }
}
