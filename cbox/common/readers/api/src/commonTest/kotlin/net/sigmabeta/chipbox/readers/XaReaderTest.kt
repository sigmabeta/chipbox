package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

private const val XA_SECTOR_SIZE = 0x930
private const val XA_PATH = "/library/PlayStation/Game/track.xa"
private const val XA_FILE = 1
private const val XA_CHANNEL = 1
private const val XA_PADDING_CHANNEL = 0xFF

// Submode bits (PSX CD-XA): 0x04 audio, 0x20 form2, 0x40 realtime, 0x80 end-of-file. An audio
// sector is form1 when the form2 bit is clear.
private const val XA_AUDIO_FORM1_SUBMODE = 0x04
private const val XA_AUDIO_FORM2_SUBMODE = 0x64
private const val XA_EOF_FLAG = 0x80

// Coding-info byte: bits 0-1 channels (0 mono / 1 stereo), 2-3 rate (0 37.8 kHz / 1 18.9 kHz),
// 4-5 bits per sample (0 4-bit / 1 8-bit).
private const val XA_CODING_STEREO_4BIT_37800 = 0x01
private const val XA_CODING_MONO_4BIT_37800 = 0x00
private const val XA_CODING_STEREO_8BIT_37800 = 0x11
private const val XA_CODING_STEREO_4BIT_18900 = 0x05
private const val XA_CODING_MONO_8BIT_37800 = 0x10

class XaReaderTest {
    private val readers = Readers(BluntHatchet())
    private val reader = XaReader(BluntHatchet())

    @Test
    fun `a raw mode2 file yields one PSX track with the native probe length`() {
        val bytes = rawXa(List(10) { xaSector() })

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, XA_PATH))

        assertEquals(1, tracks.size)
        // Native probe (meta/xa.c + xa_bytes_to_samples): 10 sectors x (28*8/2)*18 = 2016 samples
        // each = 20,160 samples at 37,800 Hz; 20,160 * 1000 / 37,800 = 533 ms (truncated).
        assertEquals(533L, tracks[0].length)
        assertEquals(1, tracks[0].trackNumber)
        assertEquals(TAG_UNKNOWN, tracks[0].title)
        assertEquals(TAG_UNKNOWN, tracks[0].artist)
        assertEquals(TAG_UNKNOWN, tracks[0].game)
        assertEquals(0L, tracks[0].fadeLengthMs)
        assertEquals(Platform.PSX, tracks[0].platform)
        assertEquals(XA_PATH, tracks[0].path)
    }

    @Test
    fun `xa is a registered version 1 streamed reader`() {
        assertSame(readers.xa, readers.forExtension("xa"))
        assertEquals(1, readers.readerVersionFor("xa"))
    }

    @Test
    fun `a RIFF-wrapped form1 file yields the form1 sample count`() {
        val bytes = riffXa(List(10) { xaSector(submode = XA_AUDIO_FORM1_SUBMODE) })

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, XA_PATH))

        assertEquals(1, tracks.size)
        // Form1 carries 16 frames per sector: 10 * (28*8/2) * 16 = 17,920 samples;
        // 17,920 * 1000 / 37,800 = 474 ms (native probe).
        assertEquals(474L, tracks[0].length)
    }

    @Test
    fun `interleaved file and channel streams become separate 1-based tracks`() {
        // 0101 for 7 sectors and 0102 for 5, interleaved like a real CD-XA track.
        val sectors = buildList {
            repeat(5) {
                add(xaSector(channel = 1))
                add(xaSector(channel = 2))
            }
            add(xaSector(channel = 1))
            add(xaSector(channel = 1))
        }

        val tracks = assertNotNull(reader.readTracksFromFile(rawXa(sectors), XA_PATH))

        assertEquals(2, tracks.size)
        // Native probe: subsong 1 "0101" has 7 sectors = 14,112 samples -> 373 ms;
        // subsong 2 "0102" has 5 sectors = 10,080 samples -> 266 ms.
        assertEquals(listOf(1, 2), tracks.map { it.trackNumber })
        assertEquals(listOf(373L, 266L), tracks.map { it.length })
        // A multi-subsong XA file's stream name is its file+channel marker, as upstream exposes it.
        assertEquals(listOf("0101", "0102"), tracks.map { it.title })
    }

    @Test
    fun `8-bit stereo XA uses the 8-bit subframe count`() {
        val bytes = rawXa(List(10) { xaSector(codingInfo = XA_CODING_STEREO_8BIT_37800) })

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, XA_PATH))

        // 10 * (28*4/2) * 18 = 10,080 samples; 10,080 * 1000 / 37,800 = 266 ms (native probe).
        assertEquals(266L, tracks[0].length)
    }

    @Test
    fun `mono XA counts every sample once`() {
        val bytes = rawXa(List(10) { xaSector(codingInfo = XA_CODING_MONO_4BIT_37800) })

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, XA_PATH))

        // 10 * (28*8/1) * 18 = 40,320 samples; 40,320 * 1000 / 37,800 = 1066 ms (native probe).
        assertEquals(1066L, tracks[0].length)
    }

    @Test
    fun `18 point 9 kHz XA halves the sample rate`() {
        val bytes = rawXa(List(20) { xaSector(codingInfo = XA_CODING_STEREO_4BIT_18900) })

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, XA_PATH))

        // 20 * 2,016 = 40,320 samples at 18,900 Hz; 40,320 * 1000 / 18,900 = 2133 ms (native probe).
        assertEquals(2133L, tracks[0].length)
    }

    @Test
    fun `8-bit mono is rejected exactly like upstream`() {
        // Upstream xa.c refuses bps=8 with mono ("untested"); a silently wrong length is worse.
        val bytes = rawXa(List(10) { xaSector(codingInfo = XA_CODING_MONO_8BIT_37800) })

        assertNull(reader.readTracksFromFile(bytes, XA_PATH))
    }

    @Test
    fun `the end-of-file flag closes the subsong and the next sector starts a new one`() {
        val sectors = listOf(
            xaSector(),
            xaSector(),
            // The EOF sector still belongs to the subsong it terminates.
            xaSector(submode = XA_AUDIO_FORM2_SUBMODE or XA_EOF_FLAG),
            xaSector(),
            xaSector(),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(rawXa(sectors), XA_PATH))

        assertEquals(2, tracks.size)
        // Native probe: 3 sectors = 6,048 samples -> 160 ms, then 2 sectors = 4,032 -> 106 ms.
        assertEquals(listOf(160L, 106L), tracks.map { it.length })
    }

    @Test
    fun `channel-0xFF padding sectors are skipped`() {
        val sectors = buildList {
            repeat(5) {
                add(xaSector())
                add(xaSector(channel = XA_PADDING_CHANNEL, submode = 0x00, codingInfo = 0x00))
            }
        }

        val tracks = assertNotNull(reader.readTracksFromFile(rawXa(sectors), XA_PATH))

        assertEquals(1, tracks.size)
        // Only the 5 audio sectors count: 10,080 samples -> 266 ms (native probe).
        assertEquals(266L, tracks[0].length)
    }

    @Test
    fun `wrong magic is rejected`() {
        assertNull(reader.readTracksFromFile(ByteArray(XA_SECTOR_SIZE * 2), XA_PATH))
    }

    @Test
    fun `truncated sector data is rejected`() {
        val full = rawXa(List(3) { xaSector() })

        assertNull(reader.readTracksFromFile(full.copyOf(full.size - 1), XA_PATH))
    }

    @Test
    fun `a file with no audio sectors yields no tracks`() {
        val bytes = rawXa(List(3) { xaSector(submode = 0x00) })

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, XA_PATH))

        assertEquals(0, tracks.size)
    }
}

/** One 2352-byte raw CD sector carrying an XA subheader at 0x10 (repeated at 0x14). */
private fun xaSector(
    file: Int = XA_FILE,
    channel: Int = XA_CHANNEL,
    submode: Int = XA_AUDIO_FORM2_SUBMODE,
    codingInfo: Int = XA_CODING_STEREO_4BIT_37800,
): ByteArray {
    val sector = ByteArray(XA_SECTOR_SIZE)
    // 12-byte sync word: 00 FF x10 00, then min/sec/frame/mode. Mode 2 carries XA subheaders.
    for (i in 1..10) sector[i] = 0xFF.toByte()
    sector[15] = 0x02
    val info = (file shl 24) or (channel shl 16) or (submode shl 8) or codingInfo
    writeIntBe(sector, 0x10, info)
    writeIntBe(sector, 0x14, info)
    // Valid blank XA frames (0x0C headers) so the fixture is a legal raw-XA stream.
    for (i in 0x18 until 0x18 + 0x900) sector[i] = 0x0C
    return sector
}

private fun rawXa(sectors: List<ByteArray>): ByteArray = cat(*sectors.toTypedArray())

/** The RIFF/CDXA wrapper a CD driver puts around the same sectors; the audio starts at 0x2C. */
private fun riffXa(sectors: List<ByteArray>): ByteArray {
    val body = rawXa(sectors)
    val header = ByteArray(0x2C)
    writeFixedAscii(header, 0x00, "RIFF", 4)
    writeIntLe(header, 0x04, body.size + 0x24)
    writeFixedAscii(header, 0x08, "CDXA", 4)
    writeFixedAscii(header, 0x0C, "fmt ", 4)
    writeIntLe(header, 0x10, 0x10)
    writeFixedAscii(header, 0x20, "data", 4)
    writeIntLe(header, 0x24, body.size)
    return cat(header, body)
}
