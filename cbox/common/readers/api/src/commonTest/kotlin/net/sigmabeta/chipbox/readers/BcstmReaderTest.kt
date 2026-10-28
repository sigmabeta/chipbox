package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

private const val BCSTM_PATH = "/library/Nintendo 3DS/Game/track.bcstm"
private const val BCSTM_SIZE = 0x200
private const val BCSTM_INFO_OFFSET = 0x60
private const val BCSTM_SEEK_OFFSET = 0xA0
private const val BCSTM_DATA_OFFSET = 0xE0
private const val BCSTM_INFO_FIELDS = BCSTM_INFO_OFFSET + 0x20

// 3DS (little-endian) BOM: the bytes FF FE, which `read_u16le` reads as 0xFEFF.
private const val BCSTM_BOM_BIG_ENDIAN = 0xFEFF
private const val BCSTM_CODEC_DSP = 2
private const val BCSTM_CODEC_UNKNOWN = 3

private const val BCSTM_SECTION_INFO = 0x4000
private const val BCSTM_SECTION_SEEK = 0x4001
private const val BCSTM_SECTION_DATA = 0x4002

class BcstmReaderTest {
    private val readers = Readers(BluntHatchet())
    private val reader = BcstmReader(BluntHatchet())

    @Test
    fun `a non-looping BCSTM plays its stream sample count`() {
        val tracks = assertNotNull(reader.readTracksFromFile(bcstmFile(), BCSTM_PATH))

        assertEquals(1, tracks.size)
        // 65,536 samples / 32,768 Hz * 1,000 = 2,000 ms (native probe's truncating formula).
        assertEquals(2_000L, tracks[0].length)
        assertEquals(1, tracks[0].trackNumber)
        assertEquals(TAG_UNKNOWN, tracks[0].title)
        assertEquals(TAG_UNKNOWN, tracks[0].artist)
        assertEquals(TAG_UNKNOWN, tracks[0].game)
        assertEquals(0L, tracks[0].fadeLengthMs)
        assertEquals(Platform.OTHER, tracks[0].platform)
        assertEquals(BCSTM_PATH, tracks[0].path)
    }

    @Test
    fun `a looping BCSTM follows the loop policy`() {
        val bytes = bcstmFile(
            loopFlag = 1,
            sampleRate = 32_000,
            loopStart = 500,
            numSamples = 40_000,
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, BCSTM_PATH))

        // Loop end is the sample count: 500 + (40,000 - 500) * 2 + 10 * 32,000 = 399,500 samples
        // -> 399,500,000 / 32,000 = 12,484 ms.
        assertEquals(12_484L, tracks[0].length)
    }

    @Test
    fun `bcstm is a registered version 1 reader`() {
        assertSame(readers.bcstm, readers.forExtension("bcstm"))
        assertEquals(1, readers.readerVersionFor("bcstm"))
    }

    @Test
    fun `a wrong magic is rejected`() {
        val bytes = bcstmFile()
        writeFixedAscii(bytes, 0x00, "RSTM", 4)

        assertNull(reader.readTracksFromFile(bytes, BCSTM_PATH))
    }

    @Test
    fun `a big-endian BOM is rejected`() {
        assertNull(
            reader.readTracksFromFile(
                bcstmFile(bigEndianBom = true),
                BCSTM_PATH,
            ),
        )
    }

    @Test
    fun `a header truncated before the stream info is rejected`() {
        assertNull(reader.readTracksFromFile(bcstmFile().copyOf(BCSTM_INFO_FIELDS), BCSTM_PATH))
    }

    @Test
    fun `a missing INFO section is rejected`() {
        assertNull(
            reader.readTracksFromFile(
                bcstmFile(infoOffsetOverride = 0),
                BCSTM_PATH,
            ),
        )
    }

    @Test
    fun `a missing DATA section is rejected`() {
        assertNull(
            reader.readTracksFromFile(
                bcstmFile(dataOffsetOverride = 0),
                BCSTM_PATH,
            ),
        )
    }

    @Test
    fun `an unknown codec id is rejected`() {
        assertNull(
            reader.readTracksFromFile(
                bcstmFile(codec = BCSTM_CODEC_UNKNOWN),
                BCSTM_PATH,
            ),
        )
    }

    @Test
    fun `a recognised stream with zero channels yields no tracks`() {
        val tracks = assertNotNull(
            reader.readTracksFromFile(bcstmFile(channels = 0), BCSTM_PATH),
        )

        assertEquals(0, tracks.size)
    }
}

/**
 * A synthetic BCSTM. The 3DS container is little-endian throughout; the section table points at
 * INFO/SEEK/DATA and the stream-info record sits at INFO+0x20, the layout `meta/bcstm.c` reads.
 * [bigEndianBom] writes the wrong (`FE FF`) byte order to exercise the BOM rejection.
 */
private fun bcstmFile(
    bigEndianBom: Boolean = false,
    codec: Int = BCSTM_CODEC_DSP,
    loopFlag: Int = 0,
    channels: Int = 2,
    sampleRate: Int = 32_768,
    loopStart: Int = 0,
    numSamples: Int = 65_536,
    infoOffsetOverride: Int? = null,
    dataOffsetOverride: Int? = null,
    writeSeekMagic: Boolean = true,
): ByteArray {
    val bytes = ByteArray(BCSTM_SIZE)

    writeFixedAscii(bytes, 0x00, "CSTM", 4)
    // 3DS BOM bytes are FF FE; the BE variant is FE FF and must be rejected.
    writeShortBe(bytes, 0x04, if (bigEndianBom) BCSTM_BOM_BIG_ENDIAN else 0xFFFE)
    writeShortLe(bytes, 0x06, 0x40)
    writeIntLe(bytes, 0x08, 0x02000000)
    writeIntLe(bytes, 0x0C, BCSTM_SIZE)
    writeShortLe(bytes, 0x10, 3)

    writeSection(bytes, 0, BCSTM_SECTION_INFO, infoOffsetOverride ?: BCSTM_INFO_OFFSET)
    writeSection(bytes, 1, BCSTM_SECTION_SEEK, BCSTM_SEEK_OFFSET)
    writeSection(bytes, 2, BCSTM_SECTION_DATA, dataOffsetOverride ?: BCSTM_DATA_OFFSET)

    writeFixedAscii(bytes, BCSTM_INFO_OFFSET, "INFO", 4)
    writeIntLe(bytes, BCSTM_INFO_OFFSET + 0x04, 0x40)
    bytes[BCSTM_INFO_FIELDS + 0x00] = codec.toByte()
    bytes[BCSTM_INFO_FIELDS + 0x01] = loopFlag.toByte()
    bytes[BCSTM_INFO_FIELDS + 0x02] = channels.toByte()
    writeIntLe(bytes, BCSTM_INFO_FIELDS + 0x04, sampleRate)
    writeIntLe(bytes, BCSTM_INFO_FIELDS + 0x08, loopStart)
    writeIntLe(bytes, BCSTM_INFO_FIELDS + 0x0C, numSamples)

    if (writeSeekMagic) writeFixedAscii(bytes, BCSTM_SEEK_OFFSET, "SEEK", 4)
    writeFixedAscii(bytes, BCSTM_DATA_OFFSET, "DATA", 4)
    writeIntLe(bytes, BCSTM_DATA_OFFSET + 0x04, BCSTM_SIZE - BCSTM_DATA_OFFSET)
    return bytes
}

private fun writeSection(bytes: ByteArray, index: Int, id: Int, offset: Int) {
    val entry = 0x14 + index * 0x0c
    writeShortLe(bytes, entry, id)
    writeIntLe(bytes, entry + 0x04, offset)
    writeIntLe(bytes, entry + 0x08, 0x40)
}
