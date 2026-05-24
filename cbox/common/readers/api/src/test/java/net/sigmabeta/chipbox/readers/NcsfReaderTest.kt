package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NcsfReaderTest {

    private val hatchet = BluntHatchet()

    @Test
    fun `ncsf and minincsf are PSF family`() {
        assertTrue(isPsfFamily("ncsf"))
        assertTrue(isPsfFamily("minincsf"))
    }

    @Test
    fun `minincsf and ncsf route to the PSF reader`() {
        val readers = Readers(hatchet)
        assertEquals(readers.psf, readers.forExtension("minincsf"))
        assertEquals(readers.psf, readers.forExtension("ncsf"))
    }

    @Test
    fun `ncsf platform byte maps to NDS and tags parse`() {
        val bytes = psfFile(
            platformCode = 0x25,
            tagText = "_lib=game.ncsflib\ntitle=Prologue\ngame=PWAA 3\nlength=0:20\n",
        )

        val info = PsfReader(hatchet).readTagInfo(bytes)

        assertNotNull(info)
        assertEquals(Platform.NDS, info.platform)
        assertEquals("Prologue", info.tags["title"])
        assertEquals(listOf("game.ncsflib"), info.libReferences)
    }

    // Builds a minimal, valid PSF container: "PSF" + platform byte, empty reserved/program areas,
    // then a "[TAG]" section. Mirrors the real .minincsf layout this reader consumes.
    private fun psfFile(platformCode: Int, tagText: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('P'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte(), platformCode.toByte()))
        // reserved size, program size, program crc — all zero (no data, tags only)
        repeat(3) { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()) }
        out.write("[TAG]".toByteArray(Charsets.US_ASCII))
        out.write(tagText.toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }
}
