package net.sigmabeta.chipbox.readers

import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class RsnReaderTest {

    private val hatchet = BluntHatchet()
    private val reader = RsnReader(hatchet, SpcReader(hatchet))

    @Test
    fun `the rsn extension routes to the RSN reader`() {
        val readers = Readers(hatchet)
        assertSame(readers.rsn, readers.forExtension("rsn"))
    }

    @Test
    fun `bytes that are not a RAR archive yield null`() {
        // Not a RAR signature — junrar (JVM) rejects it; JS has no decoder. Either way: a parse miss.
        val notAnArchive = "definitely not a rar archive".encodeToByteArray()
        assertNull(reader.readTracksFromFile(notAnArchive, "/library/bogus.rsn"))
    }

    @Test
    fun `empty input yields null`() {
        assertNull(reader.readTracksFromFile(ByteArray(0), "/library/empty.rsn"))
    }
}
