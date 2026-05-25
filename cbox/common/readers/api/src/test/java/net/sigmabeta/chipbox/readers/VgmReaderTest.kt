package net.sigmabeta.chipbox.readers

import net.sigmabeta.sage.logging.BluntHatchet
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Characterization tests for [VgmReader] — pins header parsing, the sample-count → length math,
 * and the gzip (.vgz) decompression path. The gzip path is the one most at risk in the okio
 * rewrite (java.util.zip.GZIPInputStream → okio GzipSource), so a .vgz round-trip is included.
 */
internal class VgmReaderTest {

    private val reader = VgmReader(BluntHatchet())

    @Test
    fun `parses a plain VGM header and computes length from total samples`() {
        val tracks = reader.readTracksFromFile(minimalVgm(totalSamples = 44_100), "test.vgm")

        assertNotNull(tracks)
        assertEquals(1, tracks.size)
        // 44_100 samples / 44_100 Hz * 1000 = 1000 ms, no loop.
        assertEquals(1000L, tracks[0].length)
        assertEquals("Unknown", tracks[0].title)
        assertEquals(0L, tracks[0].fadeLengthMs)
    }

    @Test
    fun `a loop adds two extra loop iterations to the length and enables fade`() {
        val tracks = reader.readTracksFromFile(
            minimalVgm(totalSamples = 44_100, loopSamples = 22_050),
            "loop.vgm",
        )

        assertNotNull(tracks)
        // total + 2*loop = 44_100 + 44_100 = 88_200 samples = 2000 ms.
        assertEquals(2000L, tracks[0].length)
        assertEquals(net.sigmabeta.chipbox.models.FADE_LENGTH_MS, tracks[0].fadeLengthMs)
    }

    @Test
    fun `decompresses and parses a gzipped VGM (vgz)`() {
        val gzipped = gzip(minimalVgm(totalSamples = 44_100))

        val tracks = reader.readTracksFromFile(gzipped, "test.vgz")

        assertNotNull(tracks, "gzipped VGM should decompress and parse")
        assertEquals(1000L, tracks[0].length)
    }

    @Test
    fun `rejects a file with bad magic`() {
        val notVgm = ByteArray(0x40) { 0 }
        assertNull(reader.readTracksFromFile(notVgm, "bogus.vgm"))
    }

    private fun minimalVgm(totalSamples: Int, loopSamples: Int = 0): ByteArray {
        val vgm = ByteArray(0x40)
        "Vgm ".encodeToByteArray().copyInto(vgm, 0) // magic at offset 0
        // GD3 offset (0x14) left 0 = no tag block.
        writeIntLe(vgm, 0x18, totalSamples)
        writeIntLe(vgm, 0x20, loopSamples)
        return vgm
    }

    private fun writeIntLe(array: ByteArray, offset: Int, value: Int) {
        array[offset] = (value and 0xFF).toByte()
        array[offset + 1] = ((value shr 8) and 0xFF).toByte()
        array[offset + 2] = ((value shr 16) and 0xFF).toByte()
        array[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }
}
