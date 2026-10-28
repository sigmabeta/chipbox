package net.sigmabeta.chipbox.readers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract between a streamed reader and the scanner's existing single-track filename fallback
 * (`RealScanner.scanGroup`): a one-track result whose title is [TAG_UNKNOWN] gets its title from
 * the filename, and the reader's 1-based track number is preserved.
 *
 * Tested through the normalisation helpers rather than a full scan: `Reader` is a sealed class in
 * another module, so a runner cannot inject a test reader into `Readers`, and `RealScanner` takes a
 * full `Repository`. The helper inputs below are exactly the ones the scanner compares.
 */
class TitleFallbackContractTest {

    @Test
    fun `a blank stream name becomes the sentinel that triggers the filename fallback`() {
        assertEquals(TAG_UNKNOWN, "".orUnknown())
        assertEquals(TAG_UNKNOWN, null.orUnknown())
    }

    @Test
    fun `the filename supplies the title for a single unknown-titled file`() {
        val meta = deriveMetaFromFilename("03 Blue Moon.adx")

        assertEquals("Blue Moon", meta.title)
        assertEquals(3, meta.trackNumber)
    }

    @Test
    fun `a streamed track number is positive so the filename never replaces it`() {
        // RealScanner substitutes the filename's number only when the reader's trackNumber is
        // negative. The 1-based streamed convention therefore keeps the reader's subsong identity.
        val readerTrackNumber = subsongTrackNumber(0)

        assertTrue(readerTrackNumber >= 0)
        assertEquals(1, readerTrackNumber)
    }
}
