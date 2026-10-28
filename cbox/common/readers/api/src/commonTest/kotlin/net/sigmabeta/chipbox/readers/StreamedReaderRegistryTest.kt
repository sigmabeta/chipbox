package net.sigmabeta.chipbox.readers

import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Registration for the Tier-0 streamed readers: each extension must resolve to its reader and
 * report its current version, so previously scanned rows (stored `reader_version = 0` from the
 * native probe, or an older reader version) are re-read on the next scan.
 */
class StreamedReaderRegistryTest {

    private val readers = Readers(BluntHatchet())

    @Test
    fun `strm resolves to the STRM reader at version one`() {
        assertSame(readers.strm, readers.forExtension("strm"))
        assertEquals(1, readers.readerVersionFor("strm"))
    }

    @Test
    fun `genh resolves to the GENH reader at version two`() {
        assertSame(readers.genh, readers.forExtension("genh"))
        // 2: the codec gate invalidates folders that stored tracks for external-codec GENH files.
        assertEquals(2, readers.readerVersionFor("genh"))
    }

    @Test
    fun `seb and gms share the SEB reader at version one`() {
        assertSame(readers.seb, readers.forExtension("seb"))
        assertSame(readers.seb, readers.forExtension("gms"))
        assertEquals(1, readers.readerVersionFor("seb"))
        assertEquals(1, readers.readerVersionFor("gms"))
    }

    @Test
    fun `sad resolves to the SADL reader at version one`() {
        assertSame(readers.sadl, readers.forExtension("sad"))
        assertEquals(1, readers.readerVersionFor("sad"))
    }
}
