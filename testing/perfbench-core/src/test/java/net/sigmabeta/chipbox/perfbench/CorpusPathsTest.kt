package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The shared corpus-path + scratch-name helpers both the host and device audio axes build on. */
class CorpusPathsTest {
    @Test
    fun `makes a corpus-relative path with slash separators`() {
        val root = Files.createTempDirectory("perfbench-paths").toFile()
        val nested = File(File(root, "Game"), "track.usf")

        assertEquals("Game/track.usf", corpusRelativePath(root, nested))
        assertEquals("Game/track.usf", corpusRelativePath(root, nested.path))
    }

    @Test
    fun `scratch wav name is stable and distinct per path`() {
        val name = scratchWavName("Game/track.spc")

        assertEquals(name, scratchWavName("Game/track.spc"))
        assertNotEquals(name, scratchWavName("Game/other.spc"))
        assertTrue(name.endsWith(".wav"))
    }
}
