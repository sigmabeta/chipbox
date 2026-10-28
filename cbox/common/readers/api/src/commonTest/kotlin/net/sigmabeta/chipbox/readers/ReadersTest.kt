package net.sigmabeta.chipbox.readers

import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class ReadersTest {
    private val readers = Readers(BluntHatchet())

    @Test
    fun `registered extensions resolve to a real reader version`() {
        assertSame(readers.vgm, readers.forExtension("vgm"))

        // VgmReader overrides INITIAL_VERSION with 2 — a registered extension must not report the
        // no-reader sentinel that the scanner uses to mean "nothing parsed this format".
        assertEquals(2, readers.readerVersionFor("vgm"))
        assertNotEquals(Readers.NO_READER_VERSION, readers.readerVersionFor("vgm"))
    }

    @Test
    fun `an unregistered extension still reports the no-reader sentinel`() {
        assertEquals(Readers.NO_READER_VERSION, readers.readerVersionFor("unknown"))
    }
}
