package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

private const val BFSTM_PATH = "/library/Nintendo Wii U/Game/track.bfstm"
private const val BFSTM_SIZE = 0x400
private const val BFSTM_INFO_OFFSET = 0x60
private const val BFSTM_SEEK_OFFSET = 0xA0
private const val BFSTM_DATA_OFFSET = 0xE0
private const val BFSTM_REGN_OFFSET = 0x120
private const val BFSTM_INFO_FIELDS = BFSTM_INFO_OFFSET + 0x20
private const val BFSTM_REGION_ENTRIES = BFSTM_REGN_OFFSET + 0x20
private const val BFSTM_REGION_SIZE = 0x100

private const val BFSTM_BOM_BIG_ENDIAN = 0xFEFF
private const val BFSTM_BOM_LITTLE_ENDIAN = 0xFFFE
private const val BFSTM_BOM_WRONG = 0x1234
private const val BFSTM_CODEC_DSP = 2
private const val BFSTM_CODEC_UNKNOWN = 3

private const val BFSTM_SECTION_INFO = 0x4000
private const val BFSTM_SECTION_SEEK = 0x4001
private const val BFSTM_SECTION_DATA = 0x4002
private const val BFSTM_SECTION_REGN = 0x4003

class BfstmReaderTest {
    private val readers = Readers(BluntHatchet())
    private val reader = BfstmReader(BluntHatchet())

    @Test
    fun `a non-looping big-endian BFSTM plays its stream sample count`() {
        val tracks = assertNotNull(reader.readTracksFromFile(bfstmFile(), BFSTM_PATH))

        assertEquals(1, tracks.size)
        // 96,000 samples / 48,000 Hz * 1,000 = 2,000 ms (native probe's truncating formula).
        assertEquals(2_000L, tracks[0].length)
        assertEquals(1, tracks[0].trackNumber)
        assertEquals(TAG_UNKNOWN, tracks[0].title)
        assertEquals(TAG_UNKNOWN, tracks[0].artist)
        assertEquals(TAG_UNKNOWN, tracks[0].game)
        assertEquals(0L, tracks[0].fadeLengthMs)
        assertEquals(Platform.OTHER, tracks[0].platform)
        assertEquals(BFSTM_PATH, tracks[0].path)
    }

    @Test
    fun `a looping BFSTM follows the loop policy`() {
        val bytes = bfstmFile(loopFlag = 1, loopStart = 24_000, numSamples = 96_000)

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, BFSTM_PATH))

        // Loop end is the sample count: 24,000 + (96,000 - 24,000) * 2 + 10 * 48,000
        // = 648,000 samples -> 648,000,000 / 48,000 = 13,500 ms.
        assertEquals(13_500L, tracks[0].length)
    }

    @Test
    fun `a little-endian Switch BFSTM parses the same fields`() {
        val tracks = assertNotNull(
            reader.readTracksFromFile(bfstmFile(bigEndian = false), BFSTM_PATH),
        )

        assertEquals(2_000L, tracks[0].length)
    }

    @Test
    fun `BFSTM regions are layout, not subsongs`() {
        val bytes = bfstmFile(
            regionCount = 2,
            regions = listOf(
                BfstmRegion(startSample = 0, endSampleExclusive = 14_000),
                BfstmRegion(startSample = 14_000, endSampleExclusive = 70_000),
            ),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, BFSTM_PATH))

        // The vendored bfstm.c never sets num_streams, so the native probe exposes one subsong per
        // file; REGN only describes layout. Emit the reference stream only.
        assertEquals(1, tracks.size)
        assertEquals(listOf(1), tracks.map { it.trackNumber })
        assertEquals(listOf(2_000L), tracks.map { it.length })
    }

    @Test
    fun `a region count without a REGN section is still a single stream`() {
        val bytes = bfstmFile(regionCount = 2, includeRegn = false)

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, BFSTM_PATH))

        assertEquals(1, tracks.size)
        assertEquals(2_000L, tracks[0].length)
    }

    @Test
    fun `bfstm is a registered version 2 reader`() {
        assertSame(readers.bfstm, readers.forExtension("bfstm"))
        // 2: the single-track change invalidates folders scanned while REGN regions were tracks.
        assertEquals(2, readers.readerVersionFor("bfstm"))
    }

    @Test
    fun `a wrong magic is rejected`() {
        val bytes = bfstmFile()
        writeFixedAscii(bytes, 0x00, "CSTM", 4)

        assertNull(reader.readTracksFromFile(bytes, BFSTM_PATH))
    }

    @Test
    fun `an unknown BOM is rejected`() {
        assertNull(
            reader.readTracksFromFile(
                bfstmFile(bomOverride = BFSTM_BOM_WRONG),
                BFSTM_PATH,
            ),
        )
    }

    @Test
    fun `a header truncated before the stream info is rejected`() {
        assertNull(reader.readTracksFromFile(bfstmFile().copyOf(BFSTM_INFO_FIELDS), BFSTM_PATH))
    }

    @Test
    fun `a missing INFO section is rejected`() {
        assertNull(
            reader.readTracksFromFile(
                bfstmFile(infoOffsetOverride = 0),
                BFSTM_PATH,
            ),
        )
    }

    @Test
    fun `an unknown codec id is rejected`() {
        assertNull(
            reader.readTracksFromFile(
                bfstmFile(codec = BFSTM_CODEC_UNKNOWN),
                BFSTM_PATH,
            ),
        )
    }

    @Test
    fun `a recognised stream with zero channels yields no tracks`() {
        val tracks = assertNotNull(
            reader.readTracksFromFile(bfstmFile(channels = 0), BFSTM_PATH),
        )

        assertEquals(0, tracks.size)
    }
}

/** One REGN region: upstream reads its start and an inclusive end (then adds one). */
private data class BfstmRegion(val startSample: Int, val endSampleExclusive: Int)

/**
 * A synthetic BFSTM: an `FSTM` header with a section table (INFO/SEEK/DATA[/REGN]), a stream-info
 * record at INFO+0x20 (the layout `meta/bfstm.c` reads), and optional REGN region entries, each
 * 0x100 bytes as upstream assumes. [bigEndian] selects the Wii U BOM and field endianness; the
 * Switch form is little-endian.
 */
private fun bfstmFile(
    bigEndian: Boolean = true,
    bomOverride: Int? = null,
    codec: Int = BFSTM_CODEC_DSP,
    loopFlag: Int = 0,
    channels: Int = 2,
    regionCount: Int = 0,
    sampleRate: Int = 48_000,
    loopStart: Int = 0,
    numSamples: Int = 96_000,
    regions: List<BfstmRegion> = emptyList(),
    includeRegn: Boolean = regions.isNotEmpty(),
    infoOffsetOverride: Int? = null,
): ByteArray {
    val bytes = ByteArray(BFSTM_SIZE)
    val write32: (Int, Int) -> Unit = if (bigEndian) {
        { offset, value -> writeIntBe(bytes, offset, value) }
    } else {
        { offset, value -> writeIntLe(bytes, offset, value) }
    }
    val write16: (Int, Int) -> Unit = if (bigEndian) {
        { offset, value -> writeShortBe(bytes, offset, value) }
    } else {
        { offset, value -> writeShortLe(bytes, offset, value) }
    }
    val bom = bomOverride ?: if (bigEndian) BFSTM_BOM_BIG_ENDIAN else BFSTM_BOM_LITTLE_ENDIAN

    writeFixedAscii(bytes, 0x00, "FSTM", 4)
    // The BOM is a byte pattern, not an endian-encoded value: FE FF (Wii U) / FF FE (Switch).
    writeShortBe(bytes, 0x04, bom)
    write16(0x06, 0x40)
    write32(0x08, 0x00000400)
    write32(0x0C, BFSTM_SIZE)
    write16(0x10, if (includeRegn) 4 else 3)

    writeSection(bytes, write16, write32, 0, BFSTM_SECTION_INFO, infoOffsetOverride ?: BFSTM_INFO_OFFSET)
    writeSection(bytes, write16, write32, 1, BFSTM_SECTION_SEEK, BFSTM_SEEK_OFFSET)
    writeSection(bytes, write16, write32, 2, BFSTM_SECTION_DATA, BFSTM_DATA_OFFSET)
    if (includeRegn) {
        writeSection(bytes, write16, write32, 3, BFSTM_SECTION_REGN, BFSTM_REGN_OFFSET)
    }

    writeFixedAscii(bytes, BFSTM_INFO_OFFSET, "INFO", 4)
    write32(BFSTM_INFO_OFFSET + 0x04, 0x40)
    bytes[BFSTM_INFO_FIELDS + 0x00] = codec.toByte()
    bytes[BFSTM_INFO_FIELDS + 0x01] = loopFlag.toByte()
    bytes[BFSTM_INFO_FIELDS + 0x02] = channels.toByte()
    bytes[BFSTM_INFO_FIELDS + 0x03] = regionCount.toByte()
    write32(BFSTM_INFO_FIELDS + 0x04, sampleRate)
    write32(BFSTM_INFO_FIELDS + 0x08, loopStart)
    write32(BFSTM_INFO_FIELDS + 0x0C, numSamples)
    write32(BFSTM_INFO_FIELDS + 0x14, 0x1000) // block size
    write32(BFSTM_INFO_FIELDS + 0x24, 0x1000) // last block size

    writeFixedAscii(bytes, BFSTM_DATA_OFFSET, "DATA", 4)
    write32(BFSTM_DATA_OFFSET + 0x04, BFSTM_SIZE - BFSTM_DATA_OFFSET)

    if (includeRegn) {
        writeFixedAscii(bytes, BFSTM_REGN_OFFSET, "REGN", 4)
        write32(BFSTM_REGN_OFFSET + 0x04, BFSTM_SIZE - BFSTM_REGN_OFFSET)
        regions.forEachIndexed { index, region ->
            val entry = BFSTM_REGION_ENTRIES + index * BFSTM_REGION_SIZE
            write32(entry, region.startSample)
            // Upstream adds one to the stored end.
            write32(entry + 0x04, region.endSampleExclusive - 1)
        }
    }
    return bytes
}

private fun writeSection(
    bytes: ByteArray,
    write16: (Int, Int) -> Unit,
    write32: (Int, Int) -> Unit,
    index: Int,
    id: Int,
    offset: Int,
) {
    val entry = 0x14 + index * 0x0c
    write16(entry, id)
    write32(entry + 0x04, offset)
    write32(entry + 0x08, 0x40)
}
