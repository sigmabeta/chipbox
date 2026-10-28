package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * SEB/GMS (`cbox/native/vgmstream/Core/vgmstream/src/meta/seb.c`) — Game Arts streams: PS-ADPCM
 * in 0x800-byte interleaved blocks behind a 32-bit little-endian header.
 *
 * The expected lengths are the native probe's `play_samples * 1000 / sample_rate` under the fixed
 * loop/fade config. Corpus-backed cases were confirmed against the pre-cutover database export
 * (stored = reader length + 2 000 ms zero-fade padding).
 *
 * Two quirks matter: the loop flag is **inverted** (the field at 0x20 means "loop" when it is 0),
 * and the format has no magic — dispatch is extension-only, so malformed headers are rejected by
 * their channel/rate/offset fields.
 */
class SebReaderTest {

    private val reader = SebReader(BluntHatchet())

    @Test
    fun `a looping stereo stream adds two loop passes and ten fade seconds`() {
        // Corpus: "SCUS-94457_001.GMS" (stereo, 22 050 Hz, loop flag field 0, loop 143 360,
        // 1 834 644 samples). play = 143 360 + 1 691 284 * 2 + 220 500 = 3 746 428 -> 169 906 ms;
        // DB 171 906.
        val track = singleTrack(
            sebFile(channels = 2, rate = 22_050, loopStartSample = 143_360, numSamples = 1_834_644, loopField = 0),
        )

        assertEquals(169_906L, track.length)
        assertEquals(0L, track.fadeLengthMs)
    }

    @Test
    fun `a non-looping mono stream uses its sample count`() {
        // Corpus: "SCUS-94457_022.GMS" (mono, 22 050 Hz, loop flag field non-zero, 60 984
        // samples). 60 984 / 22 050 = 2 765 ms; DB 4 765.
        val track = singleTrack(
            sebFile(channels = 1, rate = 22_050, loopStartSample = 60_928, numSamples = 60_984, loopField = 1),
        )

        assertEquals(2_765L, track.length)
    }

    @Test
    fun `a looping mono stream`() {
        // Corpus: "SCUS-94457_018.GMS" (mono, 22 050 Hz, loop 146 944, 924 560 samples).
        // play = 146 944 + 777 616 * 2 + 220 500 = 1 922 676 -> 87 196 ms; DB 89 196.
        val track = singleTrack(
            sebFile(channels = 1, rate = 22_050, loopStartSample = 146_944, numSamples = 924_560, loopField = 0),
        )

        assertEquals(87_196L, track.length)
    }

    @Test
    fun `the loop flag is inverted - zero means loop and non-zero means no loop`() {
        // Same header twice; only 0x20 changes. A looping stream is far longer than its sample
        // count because of the two passes plus fade, so the two lengths prove which branch ran.
        val looping = singleTrack(
            sebFile(channels = 1, rate = 22_050, loopStartSample = 10_000, numSamples = 44_100, loopField = 0),
        )
        val nonLooping = singleTrack(
            sebFile(channels = 1, rate = 22_050, loopStartSample = 10_000, numSamples = 44_100, loopField = 2),
        )

        assertEquals(2_000L, nonLooping.length) // 44 100 / 22 050
        // 10 000 + 34 100 * 2 + 220 500 = 298 700 -> 13 546 ms
        assertEquals(13_546L, looping.length)
    }

    @Test
    fun `streamed tracks use the 1-based convention, unknown tags and OTHER platform`() {
        val track = singleTrack(
            sebFile(channels = 2, rate = 22_050, loopStartSample = 0, numSamples = 22_050, loopField = 1),
        )

        assertEquals(TAG_UNKNOWN, track.title)
        assertEquals(TAG_UNKNOWN, track.artist)
        assertEquals(TAG_UNKNOWN, track.game)
        assertEquals(1, track.trackNumber)
        assertEquals("", track.extension)
        assertEquals(Platform.OTHER, track.platform)
    }

    @Test
    fun `rejects a loop offset past the end of the file`() {
        // seb.c rejects either offset (0x10/0x18) larger than the stream size.
        assertNull(
            reader.readTracksFromFile(
                sebFile(
                    channels = 1,
                    rate = 22_050,
                    loopStartSample = 0,
                    numSamples = 22_050,
                    loopField = 1,
                    loopStartOffset = 0x2000,
                    fileSize = 0x1000,
                ),
                "bogus.gms",
            ),
        )
        assertNull(
            reader.readTracksFromFile(
                sebFile(
                    channels = 1,
                    rate = 22_050,
                    loopStartSample = 0,
                    numSamples = 22_050,
                    loopField = 1,
                    loopEndOffset = 0x2000,
                    fileSize = 0x1000,
                ),
                "bogus.gms",
            ),
        )
    }

    @Test
    fun `rejects more than two channels and a non-positive rate`() {
        assertNull(
            reader.readTracksFromFile(
                sebFile(channels = 3, rate = 22_050, loopStartSample = 0, numSamples = 22_050, loopField = 1),
                "bogus.gms",
            ),
        )
        assertNull(
            reader.readTracksFromFile(
                sebFile(channels = 1, rate = 0, loopStartSample = 0, numSamples = 22_050, loopField = 1),
                "bogus.gms",
            ),
        )
        assertNull(
            reader.readTracksFromFile(
                sebFile(channels = 0, rate = 22_050, loopStartSample = 0, numSamples = 22_050, loopField = 1),
                "bogus.gms",
            ),
        )
    }

    @Test
    fun `rejects a truncated header without throwing`() {
        val bytes = sebFile(
            channels = 1,
            rate = 22_050,
            loopStartSample = 0,
            numSamples = 22_050,
            loopField = 1,
            fileSize = 0x20,
        )

        assertNull(reader.readTracksFromFile(bytes, "truncated.gms"))
    }

    private fun singleTrack(bytes: ByteArray): RawTrack {
        val tracks = reader.readTracksFromFile(bytes, "test.gms")
        assertNotNull(tracks)
        assertEquals(1, tracks.size)
        return tracks[0]
    }
}

/**
 * Minimal SEB/GMS header: channels at 0x00, rate at 0x04, loop start/end file offsets at 0x10/0x18,
 * loop start sample at 0x14, sample count at 0x1c and the inverted loop field at 0x20 — the fields
 * `seb.c` reads. The 0x800-byte block payload is zero-filled and irrelevant to header parsing.
 */
private fun sebFile(
    channels: Int,
    rate: Int,
    loopStartSample: Int,
    numSamples: Int,
    loopField: Int,
    loopStartOffset: Int = 0x800,
    loopEndOffset: Int = 0x800,
    fileSize: Int = 0x1000,
): ByteArray {
    val bytes = ByteArray(maxOf(fileSize, 0x24))
    writeIntLe(bytes, 0x00, channels)
    writeIntLe(bytes, 0x04, rate)
    writeIntLe(bytes, 0x10, loopStartOffset)
    writeIntLe(bytes, 0x14, loopStartSample)
    writeIntLe(bytes, 0x18, loopEndOffset)
    writeIntLe(bytes, 0x1c, numSamples)
    writeIntLe(bytes, 0x20, loopField)
    return bytes.copyOf(fileSize)
}
