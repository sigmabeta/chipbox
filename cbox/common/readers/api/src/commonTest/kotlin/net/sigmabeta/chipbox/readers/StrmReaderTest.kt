package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * NDS STRM (`cbox/native/vgmstream/Core/vgmstream/src/meta/nds_strm.c`).
 *
 * The expected lengths are the native probe's `play_samples * 1000 / sample_rate` (integer
 * division) with the probe's fixed `loop_count = 2.0` / `fade_time = 10.0` config. Corpus-backed
 * cases were confirmed against the pre-cutover database export: the stored length is this reader
 * length **plus** the scanner's 2 000 ms zero-fade padding, e.g. `57_002 + 2_000 = 59_002`.
 */
class StrmReaderTest {

    private val reader = StrmReader(BluntHatchet())

    @Test
    fun `an IMA stream without a loop uses the sample count as its length`() {
        // Corpus: "01 Masahiko Kimura - Cross of the Blue Moon [STRM_DSCASTLEVOP].strm"
        // (codec 2, stereo, 32 728 Hz, 1 865 588 samples) — probe 57 002 ms, DB 59 002 ms.
        val track = singleTrack(
            strmFile(codec = 2, loop = false, channels = 2, rate = 32_728, loopStart = 0, numSamples = 1_865_588),
        )

        assertEquals(57_002L, track.length)
        assertEquals(0L, track.fadeLengthMs)
    }

    @Test
    fun `a looping IMA stream adds two loop passes and ten fade seconds`() {
        // Corpus: "003 In Kapp'n's Cab.strm" (codec 2, mono, 16 364 Hz, loop 49 784..663 436).
        // play_samples = 49_784 + (663_436 - 49_784) * 2 + 163_640 = 1_440_728; / 16 364 = 88 042 ms.
        // DB 90 042 ms = 88 042 + 2 000 padding.
        val track = singleTrack(
            strmFile(codec = 2, loop = true, channels = 1, rate = 16_364, loopStart = 49_784, numSamples = 663_436),
        )

        assertEquals(88_042L, track.length)
        // Readers emit the true probe length; the scanner's zero fade is what keeps its 2 s padding.
        assertEquals(0L, track.fadeLengthMs)
    }

    @Test
    fun `a PCM16 stream is parsed like IMA`() {
        // Corpus: "316 STRM_TITLE.strm" (codec 1, stereo, 32 728 Hz, 3 665 535 samples).
        // 3 665 535 / 32 728 = 111 999.xx ms -> 111 999; DB 113 999 with padding.
        val track = singleTrack(
            strmFile(codec = 1, loop = false, channels = 2, rate = 32_728, loopStart = 0, numSamples = 3_665_535),
        )

        assertEquals(111_999L, track.length)
    }

    @Test
    fun `a looping PCM16 stream follows the same loop policy`() {
        // PCM16 (codec 1) exercises the loop branch independently of IMA: 32 768 samples at
        // 32 768 Hz. play = 0 + 32 768 * 2 + 327 680 = 393 216 samples = 12 000 ms.
        val track = singleTrack(
            strmFile(codec = 1, loop = true, channels = 2, rate = 32_768, loopStart = 0, numSamples = 32_768),
        )

        assertEquals(12_000L, track.length)
    }

    @Test
    fun `a PCM8 stream at an exact second boundary`() {
        // Codec 0 (PCM8) has no corpus files but is part of the upstream reader; 32 768 samples at
        // 32 768 Hz is exactly 1 000 ms.
        val track = singleTrack(
            strmFile(codec = 0, loop = false, channels = 1, rate = 32_768, loopStart = 0, numSamples = 32_768),
        )

        assertEquals(1_000L, track.length)
    }

    @Test
    fun `streamed tracks use the 1-based convention, unknown tags and OTHER platform`() {
        val track = singleTrack(
            strmFile(codec = 2, loop = false, channels = 2, rate = 32_728, loopStart = 0, numSamples = 32_728),
        )

        // STRM carries no stream name, so the scanner's filename fallback produces the stored title.
        assertEquals(TAG_UNKNOWN, track.title)
        assertEquals(TAG_UNKNOWN, track.artist)
        assertEquals(TAG_UNKNOWN, track.game)
        assertEquals(1, track.trackNumber)
        assertEquals("", track.extension)
        assertEquals(Platform.OTHER, track.platform)
        assertEquals("test.strm", track.path)
    }

    @Test
    fun `rejects a file without the STRM magic`() {
        val bytes = strmFile(
            codec = 2,
            loop = false,
            channels = 2,
            rate = 32_728,
            loopStart = 0,
            numSamples = 32_768,
            magic = "NOPE",
        )

        assertNull(reader.readTracksFromFile(bytes, "bogus.strm"))
    }

    @Test
    fun `rejects an unknown byte-order mark`() {
        val bytes = strmFile(
            codec = 2,
            loop = false,
            channels = 2,
            rate = 32_728,
            loopStart = 0,
            numSamples = 32_768,
            bom = 0x0102_0304,
        )

        assertNull(reader.readTracksFromFile(bytes, "bogus.strm"))
    }

    @Test
    fun `rejects an unsupported codec`() {
        val bytes = strmFile(
            codec = 3,
            loop = false,
            channels = 2,
            rate = 32_728,
            loopStart = 0,
            numSamples = 32_768,
        )

        assertNull(reader.readTracksFromFile(bytes, "bogus.strm"))
    }

    @Test
    fun `rejects a truncated header without throwing`() {
        // Long enough for the magic, too short for the loop/sample fields at 0x20/0x24.
        val bytes = strmFile(
            codec = 2,
            loop = false,
            channels = 2,
            rate = 32_728,
            loopStart = 0,
            numSamples = 32_768,
        ).copyOf(0x20)

        assertNull(reader.readTracksFromFile(bytes, "truncated.strm"))
    }

    private fun singleTrack(bytes: ByteArray): RawTrack {
        val tracks = reader.readTracksFromFile(bytes, "test.strm")
        assertNotNull(tracks)
        assertEquals(1, tracks.size)
        return tracks[0]
    }
}

/**
 * Minimal STRM header: NDS "STRM" + BOM, a 0x50-byte "HEAD" chunk, then the fields `nds_strm.c`
 * reads. The audio payload is irrelevant to header parsing, so the fixture stops at 0x40.
 */
private fun strmFile(
    codec: Int,
    loop: Boolean,
    channels: Int,
    rate: Int,
    loopStart: Int,
    numSamples: Int,
    magic: String = "STRM",
    bom: Int = 0xFFFE0001.toInt(),
): ByteArray {
    val bytes = ByteArray(0x40)
    writeFixedAscii(bytes, 0x00, magic, 4)
    writeIntBe(bytes, 0x04, bom)
    writeFixedAscii(bytes, 0x10, "HEAD", 4)
    writeIntLe(bytes, 0x14, 0x50)
    bytes[0x18] = codec.toByte()
    bytes[0x19] = if (loop) 1 else 0
    bytes[0x1a] = channels.toByte()
    writeShortLe(bytes, 0x1c, rate)
    writeIntLe(bytes, 0x20, loopStart)
    writeIntLe(bytes, 0x24, numSamples)
    writeIntLe(bytes, 0x28, 0x40) // audio data start (unused by the length reader)
    writeIntLe(bytes, 0x30, 0x1000) // interleave block size
    return bytes
}
