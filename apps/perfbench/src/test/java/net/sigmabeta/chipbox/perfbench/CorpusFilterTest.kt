package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The `--filter` semantics: basename globs match at any depth, slashed globs match the full path. */
class CorpusFilterTest {
    @Test
    fun `a basename glob matches a nested file at any depth`() {
        val matches = corpusPathFilter("*.spc")

        assertTrue(matches("Game/track.spc"))
        assertTrue(matches("track.spc"))
        assertTrue(matches("A/B/C/deep.spc"))
        assertFalse(matches("Game/track.vgm"))
    }

    @Test
    fun `a folder glob matches its files across subdirectories`() {
        val matches = corpusPathFilter("Chrono Trigger/**")

        assertTrue(matches("Chrono Trigger/track.spc"))
        assertTrue(matches("Chrono Trigger/disc2/track.spc"))
        assertFalse(matches("Chrono Cross/track.spc"))
    }

    @Test
    fun `an exact relative path matches only itself`() {
        val matches = corpusPathFilter("Game/track.spc")

        assertTrue(matches("Game/track.spc"))
        assertFalse(matches("Game/other.spc"))
    }

    @Test
    fun `excludes a file that matches no pattern`() {
        assertFalse(corpusPathFilter("*.vgm")("Game/track.spc"))
    }

    @Test
    fun `rejects a malformed pattern at parse time`() {
        assertFailsWith<IllegalArgumentException> { requireValidGlob("[") }
    }
}
