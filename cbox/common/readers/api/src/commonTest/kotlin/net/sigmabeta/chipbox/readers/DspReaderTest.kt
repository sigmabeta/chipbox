package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

private const val DSP_PATH = "/library/Nintendo GameCube/Game/track.dsp"
private const val DSP_HEADER_SIZE = 0x60
private const val DSP_DATA_SIZE = 0x40
private const val DSP_COEF_COUNT = 16

// A 32 kHz mono clip with a whole number of frames: 6,400 samples / 32,000 Hz = 200 ms.
private const val DSP_RATE = 32_000
private const val DSP_SAMPLES = 6_400
private const val DSP_NIBBLE_COUNT = 0x2000
private const val DSP_INITIAL_OFFSET = 2

// Loop points are stored in nibbles: 32 nibbles = two 16-nibble frames = 28 samples, and
// 448 nibbles = 28 frames = 392 samples. Upstream adds one to the converted loop end.
private const val DSP_LOOP_START_NIBBLES = 32
private const val DSP_LOOP_END_NIBBLES = 448

class DspReaderTest {
    private val readers = Readers(BluntHatchet())
    private val reader = DspReader(BluntHatchet())

    @Test
    fun `a non-looping DSP plays its header sample count`() {
        val tracks = assertNotNull(reader.readTracksFromFile(dspFile(), DSP_PATH))

        assertEquals(1, tracks.size)
        // 6,400 samples / 32,000 Hz * 1,000 = 200 ms (native probe's truncating formula).
        assertEquals(200L, tracks[0].length)
        assertEquals(1, tracks[0].trackNumber)
        assertEquals(TAG_UNKNOWN, tracks[0].title)
        assertEquals(TAG_UNKNOWN, tracks[0].artist)
        assertEquals(TAG_UNKNOWN, tracks[0].game)
        assertEquals(0L, tracks[0].fadeLengthMs)
        assertEquals(Platform.OTHER, tracks[0].platform)
        assertEquals(DSP_PATH, tracks[0].path)
    }

    @Test
    fun `the sample count comes from the header, not from the nibble count`() {
        // 44,100 declared samples at 44,100 Hz = exactly one second even though the nibble count
        // would derive a different frame count.
        val bytes = dspFile(sampleCount = 44_100, sampleRate = 44_100, nibbleCount = 0x1000)

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, DSP_PATH))

        assertEquals(1_000L, tracks[0].length)
    }

    @Test
    fun `a looping DSP follows the loop policy`() {
        val bytes = dspFile(
            loopFlag = 1,
            loopStartNibbles = DSP_LOOP_START_NIBBLES,
            loopEndNibbles = DSP_LOOP_END_NIBBLES,
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, DSP_PATH))

        // loop start = 32/16*14 = 28 samples; loop end = 448/16*14 + 1 = 393 samples.
        // 28 + (393 - 28) * 2 + 10 * 32,000 = 320,758 samples -> 320,758,000 / 32,000 = 10,023 ms.
        assertEquals(10_023L, tracks[0].length)
    }

    @Test
    fun `a loop end past the sample count is clamped to it`() {
        val bytes = dspFile(
            loopFlag = 1,
            loopStartNibbles = DSP_LOOP_START_NIBBLES,
            // 0x2000 nibbles = 512 frames = 7,168 samples, past the 6,400-sample clip.
            loopEndNibbles = 0x2000,
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, DSP_PATH))

        // Clamped loop end = 6,400; 28 + (6,400 - 28) * 2 + 320,000 = 332,772 samples
        // -> 332,772,000 / 32,000 = 10,399 ms.
        assertEquals(10_399L, tracks[0].length)
    }

    @Test
    fun `a non-looping DSP is not lengthened by its stale loop fields`() {
        // The header still carries loop offsets; only the loop flag decides the length policy.
        val bytes = dspFile(loopFlag = 0, loopStartNibbles = 32, loopEndNibbles = 448)

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, DSP_PATH))

        assertEquals(200L, tracks[0].length)
    }

    @Test
    fun `little-endian DSP headers parse to the same metadata`() {
        val tracks = assertNotNull(
            reader.readTracksFromFile(dspFile(bigEndian = false), DSP_PATH),
        )

        assertEquals(200L, tracks[0].length)
    }

    @Test
    fun `a looping little-endian DSP follows the same loop policy`() {
        val bytes = dspFile(
            bigEndian = false,
            loopFlag = 1,
            loopStartNibbles = DSP_LOOP_START_NIBBLES,
            loopEndNibbles = DSP_LOOP_END_NIBBLES,
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, DSP_PATH))

        assertEquals(10_023L, tracks[0].length)
    }

    @Test
    fun `a loop point part-way through a frame uses the nibble remainder`() {
        // 18 nibbles = one whole frame (14 samples) + a 2-nibble remainder (which maps to 0 samples);
        // 466 nibbles = 29 frames + 2 -> 406 samples. 14 + (407 - 14) * 2 + 320,000 = 320,800
        // samples -> 320,800,000 / 32,000 = 10,025 ms.
        val bytes = dspFile(loopFlag = 1, loopStartNibbles = 18, loopEndNibbles = 466)

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, DSP_PATH))

        assertEquals(10_025L, tracks[0].length)
    }

    @Test
    fun `dsp is a registered version 1 reader`() {
        assertSame(readers.dsp, readers.forExtension("dsp"))
        assertEquals(1, readers.readerVersionFor("dsp"))
    }

    @Test
    fun `a truncated header is rejected`() {
        val full = dspFile()

        assertNull(reader.readTracksFromFile(full.copyOf(DSP_HEADER_SIZE - 1), DSP_PATH))
    }

    @Test
    fun `an empty file is rejected`() {
        assertNull(reader.readTracksFromFile(ByteArray(0), DSP_PATH))
    }

    @Test
    fun `an implausible sample rate is rejected`() {
        assertNull(reader.readTracksFromFile(dspFile(sampleRate = 100), DSP_PATH))
    }

    @Test
    fun `a zero sample count is rejected`() {
        assertNull(reader.readTracksFromFile(dspFile(sampleCount = 0), DSP_PATH))
    }

    @Test
    fun `a non-ADPCM format is rejected`() {
        assertNull(reader.readTracksFromFile(dspFile(format = 1), DSP_PATH))
    }

    @Test
    fun `a non-zero gain is rejected`() {
        assertNull(reader.readTracksFromFile(dspFile(gain = 1), DSP_PATH))
    }

    @Test
    fun `all-zero coefficients are rejected`() {
        assertNull(
            reader.readTracksFromFile(dspFile(coefs = IntArray(DSP_COEF_COUNT)), DSP_PATH),
        )
    }

    @Test
    fun `a header whose predictor does not match the first data byte is rejected`() {
        assertNull(reader.readTracksFromFile(dspFile(firstDataByte = 0x11), DSP_PATH))
    }

    @Test
    fun `a file of zeros is rejected`() {
        assertNull(reader.readTracksFromFile(ByteArray(DSP_HEADER_SIZE + DSP_DATA_SIZE), DSP_PATH))
    }
}

/**
 * A synthetic standalone DSP: a 0x60-byte DSPADPCM header (see `meta/ngc_dsp_std.c`'s
 * `dsp_header_t`) followed by frame data whose first byte repeats the header's initial
 * predictor/scale, as upstream validates.
 */
private fun dspFile(
    bigEndian: Boolean = true,
    sampleCount: Int = DSP_SAMPLES,
    nibbleCount: Int = DSP_NIBBLE_COUNT,
    sampleRate: Int = DSP_RATE,
    loopFlag: Int = 0,
    loopStartNibbles: Int = 0,
    loopEndNibbles: Int = 0,
    initialOffset: Int = DSP_INITIAL_OFFSET,
    initialPs: Int = 0,
    gain: Int = 0,
    format: Int = 0,
    coefs: IntArray = IntArray(DSP_COEF_COUNT) { it + 1 },
    firstDataByte: Int = initialPs,
): ByteArray {
    val bytes = ByteArray(DSP_HEADER_SIZE + DSP_DATA_SIZE)
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

    write32(0x00, sampleCount)
    write32(0x04, nibbleCount)
    write32(0x08, sampleRate)
    write16(0x0c, loopFlag)
    write16(0x0e, format)
    write32(0x10, loopStartNibbles)
    write32(0x14, loopEndNibbles)
    write32(0x18, initialOffset)
    coefs.forEachIndexed { i, coef -> write16(0x1c + i * 2, coef) }
    write16(0x3c, gain)
    write16(0x3e, initialPs)
    // Histories, loop predictor/scale, channels and block size stay zero (all optional here).
    bytes[DSP_HEADER_SIZE] = firstDataByte.toByte()
    return bytes
}
