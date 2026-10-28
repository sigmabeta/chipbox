package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val BRSTM_PATH = "/library/Nintendo Wii/Game/track.brstm"
private const val BRSTM_SIZE = 0x200
private const val BRSTM_HEAD_OFFSET = 0x40
private const val BRSTM_STREAM_INFO_OFFSET = 0x60
private const val BRSTM_FILE_SIZE_OFFSET = 0x08

// Upstream's version field is 0.1 for the small legacy header and 1.0 for the normal chunk table.
private const val BRSTM_VERSION_STANDARD = 0x0100
private const val BRSTM_VERSION_LEGACY = 0x0001
private const val BRSTM_BOM_BIG_ENDIAN = 0xFEFF
private const val BRSTM_BOM_WRONG = 0xFFFE
private const val BRSTM_CODEC_DSP = 2
private const val BRSTM_CODEC_UNKNOWN = 3

class BrstmReaderTest {
    private val readers = Readers(BluntHatchet())
    private val reader = BrstmReader(BluntHatchet())

    @Test
    fun `a non-looping BRSTM plays its stream sample count`() {
        val tracks = assertNotNull(reader.readTracksFromFile(brstmFile(), BRSTM_PATH))

        assertEquals(1, tracks.size)
        // 96,000 samples / 48,000 Hz * 1,000 = 2,000 ms (native probe's truncating formula).
        assertEquals(2_000L, tracks[0].length)
        assertEquals(1, tracks[0].trackNumber)
        assertEquals(TAG_UNKNOWN, tracks[0].title)
        assertEquals(TAG_UNKNOWN, tracks[0].artist)
        assertEquals(TAG_UNKNOWN, tracks[0].game)
        assertEquals(0L, tracks[0].fadeLengthMs)
        assertEquals(Platform.OTHER, tracks[0].platform)
        assertEquals(BRSTM_PATH, tracks[0].path)
    }

    @Test
    fun `a looping BRSTM follows the loop policy`() {
        val bytes = brstmFile(
            loopFlag = 1,
            sampleRate = 32_000,
            loopStart = 1_000,
            numSamples = 100_000,
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, BRSTM_PATH))

        // The container's loop end is its sample count: 1,000 + (100,000 - 1,000) * 2
        // + 10 * 32,000 = 519,000 samples -> 519,000,000 / 32,000 = 16,218 ms.
        assertEquals(16_218L, tracks[0].length)
    }

    @Test
    fun `the legacy compact header is located from the head size`() {
        val bytes = brstmFile(version = BRSTM_VERSION_LEGACY)

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, BRSTM_PATH))

        assertEquals(2_000L, tracks[0].length)
    }

    @Test
    fun `a brstmspm file halves a 44 point 1 kHz rate exactly like upstream`() {
        val readers = Readers(BluntHatchet())
        val bytes = brstmFile(sampleRate = 44_100, numSamples = 44_100)

        val tracks = assertNotNull(
            readers.forExtension("brstmspm")?.readTracksFromFile(bytes, BRSTM_PATH),
        )

        // Super Paper Mario's fake extension: 44,100 in the header, 22,050 at playback.
        // 44,100 samples / 22,050 Hz * 1,000 = 2,000 ms (not the header's 1,000 ms).
        assertEquals(2_000L, tracks[0].length)
    }

    @Test
    fun `brstm and brstmspm are separate registered version 1 readers`() {
        assertSame(readers.brstm, readers.forExtension("brstm"))
        assertSame(readers.brstmSpm, readers.forExtension("brstmspm"))
        assertEquals(1, readers.readerVersionFor("brstm"))
        assertEquals(1, readers.readerVersionFor("brstmspm"))
        assertTrue(readers.brstm !== readers.brstmSpm)
    }

    @Test
    fun `a wrong magic is rejected`() {
        val bytes = brstmFile()
        writeFixedAscii(bytes, 0x00, "FSTM", 4)

        assertNull(reader.readTracksFromFile(bytes, BRSTM_PATH))
    }

    @Test
    fun `a mismatched BOM is rejected`() {
        assertNull(
            reader.readTracksFromFile(
                brstmFile(bom = BRSTM_BOM_WRONG),
                BRSTM_PATH,
            ),
        )
    }

    @Test
    fun `a truncated file is rejected by the declared size check`() {
        val full = brstmFile()

        assertNull(reader.readTracksFromFile(full.copyOf(full.size - 1), BRSTM_PATH))
    }

    @Test
    fun `an unknown codec id is rejected`() {
        assertNull(
            reader.readTracksFromFile(
                brstmFile(codec = BRSTM_CODEC_UNKNOWN),
                BRSTM_PATH,
            ),
        )
    }

    @Test
    fun `a recognised stream with zero channels yields no tracks`() {
        val tracks = assertNotNull(
            reader.readTracksFromFile(brstmFile(channels = 0), BRSTM_PATH),
        )

        assertEquals(0, tracks.size)
    }
}

/**
 * A synthetic BRSTM. `stream info` 1.0 lives at HEAD+0x20 (upstream's hard-coded
 * `info_offset = head_offset + 0x20`); the legacy 0.1 header puts it at HEAD+0x08. Fields follow
 * `meta/brstm.c` / Wiibrew's HEAD layout, with a correctly-sized DATA chunk so the file-size
 * check passes.
 */
private fun brstmFile(
    version: Int = BRSTM_VERSION_STANDARD,
    bom: Int = BRSTM_BOM_BIG_ENDIAN,
    codec: Int = BRSTM_CODEC_DSP,
    loopFlag: Int = 0,
    channels: Int = 2,
    sampleRate: Int = 48_000,
    loopStart: Int = 0,
    numSamples: Int = 96_000,
    declaredFileSize: Int = BRSTM_SIZE,
): ByteArray {
    val bytes = ByteArray(BRSTM_SIZE)
    val headOffset = BRSTM_HEAD_OFFSET
    val infoOffset = if (version == BRSTM_VERSION_LEGACY) {
        headOffset + 0x08
    } else {
        BRSTM_STREAM_INFO_OFFSET
    }

    writeFixedAscii(bytes, 0x00, "RSTM", 4)
    writeShortBe(bytes, 0x04, bom)
    writeShortBe(bytes, 0x06, version)
    writeIntBe(bytes, BRSTM_FILE_SIZE_OFFSET, declaredFileSize)
    writeShortBe(bytes, 0x0C, headOffset)
    writeShortBe(bytes, 0x0E, 2)
    writeIntBe(bytes, 0x10, headOffset)
    writeIntBe(bytes, 0x14, 0x60)
    writeIntBe(bytes, 0x18, 0x120)
    writeIntBe(bytes, 0x1C, 0x20)
    writeIntBe(bytes, 0x20, 0x180)
    writeIntBe(bytes, 0x24, BRSTM_SIZE - 0x180)

    writeFixedAscii(bytes, headOffset, "HEAD", 4)
    writeIntBe(bytes, headOffset + 0x04, 0x60)
    if (version != BRSTM_VERSION_LEGACY) {
        // HEAD+0x08: reference to stream info, offset 0x18 relative to HEAD+0x08.
        writeShortBe(bytes, headOffset + 0x08, 0x0100)
        writeIntBe(bytes, headOffset + 0x0C, 0x18)
    }

    bytes[infoOffset + 0x00] = codec.toByte()
    bytes[infoOffset + 0x01] = loopFlag.toByte()
    bytes[infoOffset + 0x02] = channels.toByte()
    writeShortBe(bytes, infoOffset + 0x04, sampleRate)
    writeIntBe(bytes, infoOffset + 0x08, loopStart)
    writeIntBe(bytes, infoOffset + 0x0C, numSamples)
    writeIntBe(bytes, infoOffset + 0x10, 0x190) // absolute audio-data offset
    writeIntBe(bytes, infoOffset + 0x18, 0x1000)
    writeIntBe(bytes, infoOffset + 0x28, 0x1000)

    writeFixedAscii(bytes, 0x180, "DATA", 4)
    writeIntBe(bytes, 0x184, BRSTM_SIZE - 0x180)
    return bytes
}
