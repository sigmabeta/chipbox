package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * GENH (`cbox/native/vgmstream/Core/vgmstream/src/meta/genh.c`) — vgmstream's generic wrapper
 * around headerless streams.
 *
 * Expected lengths are the native probe's `play_samples * 1000 / sample_rate`, with looped files
 * playing the intro, two loop passes and a ten-second fade. Corpus-backed cases were confirmed
 * against the pre-cutover database export (stored = reader length + 2 000 ms zero-fade padding).
 *
 * Header-size gating (`startOffset` / `headerSize`, 0 meaning 0x800) and the two sample-count
 * sources (explicit field at 0x40 vs loop-end fallback) are the behaviours this reader adds over
 * a naive GENH parse.
 */
class GenhReaderTest {

    private val reader = GenhReader(BluntHatchet())

    @Test
    fun `a non-looping stream uses its explicit sample count`() {
        // Corpus: "MUSIC.VB2__0_11.genh" (PSX, stereo, 44 100 Hz, loop_start = -1, 114 688
        // samples) — probe 2 600 ms, DB 4 600 ms.
        val track = singleTrack(
            genhFile(
                codec = 0,
                channels = 2,
                rate = 44_100,
                loopStart = -1,
                loopEnd = 114_688,
                numSamples = 114_688,
            ),
        )

        assertEquals(2_600L, track.length)
        assertEquals(0L, track.fadeLengthMs)
    }

    @Test
    fun `a looping stream adds two loop passes and ten fade seconds`() {
        // Corpus: "MUSIC.VB2__0_02.genh" (44 046 Hz, loop 286 720..2 637 824).
        // play = 286 720 + (2 637 824 - 286 720) * 2 + 440 460 = 5 429 388; / 44 046 = 123 266 ms.
        // DB 125 266 = 123 266 + 2 000.
        val track = singleTrack(
            genhFile(
                codec = 0,
                channels = 2,
                rate = 44_046,
                loopStart = 286_720,
                loopEnd = 2_637_824,
                numSamples = 2_637_824,
            ),
        )

        assertEquals(123_266L, track.length)
    }

    @Test
    fun `the explicit sample count wins over the loop end`() {
        // Corpus: "MUSIC.VB2__2_21.genh" (42 647 Hz, loop 344 064..1 204 224, explicit 1 261 568).
        // play = 344 064 + 860 160 * 2 + 426 470 = 2 490 854; / 42 647 = 58 406 ms; DB 60 406.
        val track = singleTrack(
            genhFile(
                codec = 0,
                channels = 2,
                rate = 42_647,
                loopStart = 344_064,
                loopEnd = 1_204_224,
                numSamples = 1_261_568,
            ),
        )

        assertEquals(58_406L, track.length)
    }

    @Test
    fun `a missing explicit sample count falls back to the loop end`() {
        // Corpus: "Track 01_290efc50.GENH" (12 000 Hz, loop_start 12 000, loop_end 1 390 000,
        // explicit count 0). play = 12 000 + 1 378 000 * 2 + 120 000 = 2 888 000 -> 240 666 ms;
        // DB 242 666.
        val track = singleTrack(
            genhFile(
                codec = 0,
                channels = 1,
                rate = 12_000,
                loopStart = 12_000,
                loopEnd = 1_390_000,
                numSamples = 0,
            ),
        )

        assertEquals(240_666L, track.length)
    }

    @Test
    fun `header size zero means the old 0x800 layout`() {
        // Old GENH files left headerSize at 0; upstream then uses 0x800 for both offsets, which
        // also puts the header past 0x100 so the explicit sample count is read (0 here -> fallback
        // to loop_end 16 384). 16 384 / 32 768 = exactly 500 ms.
        val track = singleTrack(
            genhFile(
                codec = 4, channels = 1, rate = 32_768,
                loopStart = -1, loopEnd = 16_384, numSamples = 0,
                startOffset = 0x123, headerSize = 0, fileSize = 0x1000,
            ),
        )

        assertEquals(500L, track.length)
    }

    @Test
    fun `a header shorter than 0x100 does not read the extended sample fields`() {
        // headerSize 0x24 is the minimum GENH header: the explicit count at 0x40 must be ignored
        // even though the bytes exist in the fixture, leaving the loop end as the sample count.
        val track = singleTrack(
            genhFile(
                codec = 0,
                channels = 1,
                rate = 32_768,
                loopStart = -1,
                loopEnd = 16_384,
                numSamples = 999_999,
                startOffset = 0x24,
                headerSize = 0x24,
            ),
        )

        assertEquals(500L, track.length)
    }

    @Test
    fun `a looping stream is detected by a loop start other than minus one`() {
        // loop_start = 0 is a real loop (only -1 means "no loop"). Rate chosen for exact numbers:
        // play = 0 + 44 100 * 2 + 441 000 = 529 200 samples = 12 000 ms at 44 100 Hz.
        val track = singleTrack(
            genhFile(
                codec = 0,
                channels = 1,
                rate = 44_100,
                loopStart = 0,
                loopEnd = 44_100,
                numSamples = 44_100,
            ),
        )

        assertEquals(12_000L, track.length)
    }

    @Test
    fun `streamed tracks use the 1-based convention, unknown tags and OTHER platform`() {
        val track = singleTrack(
            genhFile(
                codec = 0,
                channels = 2,
                rate = 44_100,
                loopStart = -1,
                loopEnd = 44_100,
                numSamples = 44_100,
            ),
        )

        assertEquals(TAG_UNKNOWN, track.title)
        assertEquals(TAG_UNKNOWN, track.artist)
        assertEquals(TAG_UNKNOWN, track.game)
        assertEquals(1, track.trackNumber)
        assertEquals("", track.extension)
        assertEquals(Platform.OTHER, track.platform)
    }

    @Test
    fun `rejects a file without the GENH magic`() {
        val bytes = genhFile(
            codec = 0,
            channels = 1,
            rate = 44_100,
            loopStart = -1,
            loopEnd = 44_100,
            numSamples = 44_100,
            magic = "NOPE",
        )

        assertNull(reader.readTracksFromFile(bytes, "bogus.genh"))
    }

    @Test
    fun `rejects a header size below the 0x24 minimum`() {
        val bytes = genhFile(
            codec = 0,
            channels = 1,
            rate = 44_100,
            loopStart = -1,
            loopEnd = 44_100,
            numSamples = 44_100,
            startOffset = 0x23,
            headerSize = 0x23,
        )

        assertNull(reader.readTracksFromFile(bytes, "bogus.genh"))
    }

    @Test
    fun `rejects a data offset that starts before the header ends`() {
        val bytes = genhFile(
            codec = 0,
            channels = 1,
            rate = 44_100,
            loopStart = -1,
            loopEnd = 44_100,
            numSamples = 44_100,
            startOffset = 0x100,
            headerSize = 0x200,
        )

        assertNull(reader.readTracksFromFile(bytes, "bogus.genh"))
    }

    @Test
    fun `rejects zero channels and a non-positive sample rate`() {
        assertNull(
            reader.readTracksFromFile(
                genhFile(
                    codec = 0,
                    channels = 0,
                    rate = 44_100,
                    loopStart = -1,
                    loopEnd = 44_100,
                    numSamples = 44_100,
                ),
                "bogus.genh",
            ),
        )
        assertNull(
            reader.readTracksFromFile(
                genhFile(
                    codec = 0,
                    channels = 1,
                    rate = 0,
                    loopStart = -1,
                    loopEnd = 44_100,
                    numSamples = 44_100,
                ),
                "bogus.genh",
            ),
        )
    }

    @Test
    fun `rejects a truncated header without throwing`() {
        val bytes = genhFile(
            codec = 0,
            channels = 1,
            rate = 44_100,
            loopStart = -1,
            loopEnd = 44_100,
            numSamples = 44_100,
            fileSize = 0x20,
        )

        assertNull(reader.readTracksFromFile(bytes, "truncated.genh"))
    }

    @Test
    fun `every codec on the GENH allow-list yields a track`() {
        // The vendored `switch (genh.codec)` cases that are not behind VGM_USE_* (meta/genh.c).
        val allowed = listOf(0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 24, 25, 26, 27)

        for (codec in allowed) {
            val tracks = reader.readTracksFromFile(
                genhFile(
                    codec = codec,
                    channels = 1,
                    rate = 44_100,
                    loopStart = -1,
                    loopEnd = 44_100,
                    numSamples = 44_100,
                ),
                "allowed.genh",
            )

            assertEquals(1, tracks?.size, "codec $codec should be decodable by the vendored build")
        }
    }

    @Test
    fun `a codec the vendored build cannot decode yields no tracks`() {
        // 8 = MPEG; 18..23 = ATRAC3/ATRAC3plus/XMA1/XMA2/FFmpeg/AC3 (behind VGM_USE_MPEG or
        // VGM_USE_FFMPEG); 28 = AAC; anything above the enum is unknown. Each is recognised but
        // unsupported, so it must yield no track rather than a length the player cannot decode.
        for (codec in listOf(8, 18, 19, 20, 21, 22, 23, 28, 29, 0x7F)) {
            val tracks = reader.readTracksFromFile(
                genhFile(
                    codec = codec,
                    channels = 1,
                    rate = 44_100,
                    loopStart = -1,
                    loopEnd = 44_100,
                    numSamples = 44_100,
                ),
                "unsupported.genh",
            )

            assertEquals(emptyList(), tracks, "codec $codec must not advertise a track")
        }
    }

    private fun singleTrack(bytes: ByteArray): RawTrack {
        val tracks = reader.readTracksFromFile(bytes, "test.genh")
        assertNotNull(tracks)
        assertEquals(1, tracks.size)
        return tracks[0]
    }
}

/**
 * Minimal GENH header: magic, then the little-endian fields `parse_genh` reads (channels,
 * interleave, rate, loop points, codec, data offset, header size, extended count/data size).
 * The payload is zero-filled and irrelevant to header-only parsing.
 */
private fun genhFile(
    codec: Int,
    channels: Int,
    rate: Int,
    loopStart: Int,
    loopEnd: Int,
    numSamples: Int,
    startOffset: Int = 0x1000,
    headerSize: Int = 0x1000,
    dataSize: Int = 0,
    magic: String = "GENH",
    fileSize: Int = maxOf(startOffset, 0x2000),
): ByteArray {
    val bytes = ByteArray(maxOf(fileSize, 0x54))
    writeFixedAscii(bytes, 0x00, magic, 4)
    writeIntLe(bytes, 0x04, channels)
    writeIntLe(bytes, 0x08, 0) // interleave
    writeIntLe(bytes, 0x0c, rate)
    writeIntLe(bytes, 0x10, loopStart)
    writeIntLe(bytes, 0x14, loopEnd)
    writeIntLe(bytes, 0x18, codec)
    writeIntLe(bytes, 0x1c, startOffset)
    writeIntLe(bytes, 0x20, headerSize)
    writeIntLe(bytes, 0x40, numSamples)
    writeIntLe(bytes, 0x50, dataSize)
    return bytes.copyOf(fileSize)
}
