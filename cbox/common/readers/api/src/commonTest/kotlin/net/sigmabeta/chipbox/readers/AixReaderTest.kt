package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * CRI AIX (`.aix`) metadata reader.
 *
 * AIX is a **segmented ADX container**, not an HCA cue sheet: upstream's `meta/aix.c` joins its
 * segments into one vgmstream and derives the loop from segment boundaries. Fixtures are synthetic
 * bytes laid out as `meta/aix.c` reads them (the segment/layer tables; payload bytes are zero-filled
 * because the reader never parses them) — there is **no AIX file in the local corpus**, so every
 * expectation is a literal derived from the upstream arithmetic.
 */
class AixReaderTest {
    private val reader = AixReader(BluntHatchet())

    @Test
    fun `the aix extension resolves to the AIX reader at version 1`() {
        val readers = Readers(BluntHatchet())

        assertSame(readers.aix, readers.forExtension("aix"))
        assertEquals(1, readers.readerVersionFor("aix"))
    }

    @Test
    fun `a two-segment file loops from the second segment under the two-loop fade policy`() {
        // Segment 0 is the intro (1 s), segment 1 the loop body (2 s), both at 44.1 kHz.
        val bytes = aixFile(
            segments = listOf(
                AixSegment(size = 0x100, samples = 44_100, rate = 44_100),
                AixSegment(size = 0x200, samples = 88_200, rate = 44_100),
            ),
        )

        val header = assertNotNull(readAixHeader(bytes))
        assertEquals(2, header.segmentCount)
        assertEquals(44_100, header.sampleRate)
        // num_samples = 44_100 + 88_200; loop_start_segment = loop_end_segment = 1 for two segments.
        assertEquals(132_300L, header.numSamples)
        assertTrue(header.loops)
        assertEquals(44_100L, header.loopStartSample)
        assertEquals(132_300L, header.loopEndSample)

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.aix"))

        // 44_100 + (132_300 - 44_100) * 2 + 10 * 44_100 = 661_500 samples
        // 661_500 * 1000 / 44_100 = 15_000 ms (truncating).
        assertEquals(15_000L, track.length)
        assertEquals(1, track.trackNumber)
        assertEquals(TAG_UNKNOWN, track.title)
        assertEquals(TAG_UNKNOWN, track.artist)
        assertEquals(TAG_UNKNOWN, track.game)
        assertEquals(0L, track.fadeLengthMs)
        assertEquals("music/song.aix", track.path)
        assertEquals("", track.source)
        assertEquals(Platform.OTHER, track.platform)
    }

    @Test
    fun `a five-segment file loops from the second segment to the penultimate one`() {
        val bytes = aixFile(
            segments = listOf(
                AixSegment(size = 0x100, samples = 11_025, rate = 44_100),
                AixSegment(size = 0x100, samples = 22_050, rate = 44_100),
                AixSegment(size = 0x100, samples = 33_075, rate = 44_100),
                AixSegment(size = 0x100, samples = 22_050, rate = 44_100),
                AixSegment(size = 0x100, samples = 11_025, rate = 44_100),
            ),
        )

        val header = assertNotNull(readAixHeader(bytes))
        assertEquals(5, header.segmentCount)
        assertEquals(99_225L, header.numSamples)
        assertTrue(header.loops)
        // loop_start_segment = 2 and loop_end_segment = count - 2 = 3.
        assertEquals(33_075L, header.loopStartSample) // segments 0+1
        assertEquals(88_200L, header.loopEndSample) // segments 0..3

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.aix"))

        // 33_075 + (88_200 - 33_075) * 2 + 10 * 44_100 = 584_325 samples
        // 584_325 * 1000 / 44_100 = 13_250 ms (truncating).
        assertEquals(13_250L, track.length)
    }

    @Test
    fun `a six-segment file is not looped and reports the summed segment samples`() {
        // Upstream only loops 1-5 segments; 6+ are "dynamic parts" containers and play straight.
        val bytes = aixFile(segments = List(6) { AixSegment(size = 0x100, samples = 22_050, rate = 44_100) })

        val header = assertNotNull(readAixHeader(bytes))
        assertEquals(6, header.segmentCount)
        assertEquals(132_300L, header.numSamples)
        assertFalse(header.loops)
        assertEquals(0L, header.loopStartSample)
        assertEquals(0L, header.loopEndSample)

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.aix"))

        // 132_300 samples at 44.1 kHz = 3_000 ms, no fade.
        assertEquals(3_000L, track.length)
    }

    @Test
    fun `a single-segment file does not loop`() {
        // A one-segment AIX is a plain ADX. Upstream asks for a loop at segment 1, which does not
        // exist, and setup_vgmstream then clears the empty loop, so the stream plays straight.
        val bytes = aixFile(segments = listOf(AixSegment(size = 0x100, samples = 88_200, rate = 44_100)))

        val header = assertNotNull(readAixHeader(bytes))
        assertEquals(1, header.segmentCount)
        assertEquals(88_200L, header.numSamples)
        assertFalse(header.loops)

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.aix"))

        // 88_200 samples at 44.1 kHz = 2_000 ms.
        assertEquals(2_000L, track.length)
    }

    @Test
    fun `segments after the first may inherit the first segment's sample rate`() {
        // Upstream treats a zero rate on segments > 0 as "same as segment 0".
        val bytes = aixFile(
            segments = listOf(
                AixSegment(size = 0x100, samples = 44_100, rate = 44_100),
                AixSegment(size = 0x100, samples = 44_100, rate = 0),
            ),
        )

        val header = assertNotNull(readAixHeader(bytes))
        assertEquals(44_100, header.sampleRate)
        assertEquals(88_200L, header.numSamples)
        assertTrue(header.loops)

        val track = singleTrack(reader.readTracksFromFile(bytes, "music/song.aix"))

        // 44_100 + (88_200 - 44_100) * 2 + 10 * 44_100 = 573_300 samples = 13_000 ms.
        assertEquals(13_000L, track.length)
    }

    @Test
    fun `a truncated three-segment file plays its intro and loop without looping`() {
        // Upstream's special case (Metroid: Other M): the third segment is missing on disc, so the
        // file is treated as its first two segments with looping force-disabled.
        val segments = List(3) { AixSegment(size = 0x100, samples = 22_050, rate = 44_100) }
        val full = aixFile(segments = segments)
        // Cut the file inside segment 1's declared range: segment 1 keeps only 0x40 bytes.
        val truncated = full.copyOf(AIX_DATA_OFFSET + 0x100 + 0x40)

        val header = assertNotNull(readAixHeader(truncated))
        assertEquals(2, header.segmentCount)
        assertFalse(header.loops)
        // num_samples is still taken from the header table: 22_050 + 22_050.
        assertEquals(44_100L, header.numSamples)

        val track = singleTrack(reader.readTracksFromFile(truncated, "music/truncated.aix"))

        // 44_100 samples at 44.1 kHz = 1_000 ms, no fade.
        assertEquals(1_000L, track.length)
    }

    @Test
    fun `rejects a file with bad magic`() {
        val bytes = aixFile(oneSegment()).also { writeFixedAscii(it, 0x00, "AIXG", 4) }

        assertRejected(bytes, "bad magic")
    }

    @Test
    fun `rejects an unsupported version or header size`() {
        val badVersion = aixFile(oneSegment()).also { writeIntBe(it, 0x08, 0x01000015) }
        val badSize = aixFile(oneSegment()).also { writeIntBe(it, 0x0C, 0x00000801) }

        assertRejected(badVersion, "version dword")
        assertRejected(badSize, "header size dword")
    }

    @Test
    fun `rejects a segment count outside 1 to 120`() {
        assertRejected(aixFile(emptyList()), "zero segments")
        assertRejected(aixFile(List(121) { AixSegment(size = 0, samples = 0, rate = 44_100) }), "121 segments")
    }

    @Test
    fun `rejects a segment table that overlaps the audio data`() {
        val bytes = aixFile(twoSegments()).also { writeIntBe(it, 0x04, 0x40 - 0x08) }

        assertRejected(bytes, "segment table at the data offset")
    }

    @Test
    fun `rejects a first segment that does not start at the data offset`() {
        val bytes = aixFile(twoSegments()).also { writeIntBe(it, 0x20, AIX_DATA_OFFSET + 0x100) }

        assertRejected(bytes, "first segment offset")
    }

    @Test
    fun `rejects segments with mismatched sample rates`() {
        val bytes = aixFile(
            segments = listOf(
                AixSegment(size = 0x100, samples = 44_100, rate = 44_100),
                AixSegment(size = 0x100, samples = 44_100, rate = 48_000),
            ),
        )

        assertRejected(bytes, "mismatched segment rates")
    }

    @Test
    fun `rejects a missing subtable marker`() {
        assertRejected(aixFile(twoSegments(), subtableMarker = 0x00), "subtable marker")
    }

    @Test
    fun `rejects a layer count below one`() {
        assertRejected(aixFile(twoSegments(), layerCount = 0), "empty layer table")
    }

    @Test
    fun `rejects a layer whose sample rate differs from the segments`() {
        assertRejected(
            aixFile(twoSegments(), layerRates = listOf(44_100, 48_000)),
            "mismatched layer rate",
        )
    }

    @Test
    fun `rejects a layer table that overlaps the audio data`() {
        // Segment table ends at 0x40, layer list starts at 0x50 and ends at 0x68.
        val overlappingList = aixFile(twoSegments()).also { writeIntBe(it, 0x04, 0x50 - 0x08) }
        val overlappingEnd = aixFile(twoSegments()).also { writeIntBe(it, 0x04, 0x60 - 0x08) }

        assertRejected(overlappingList, "layer list at the data offset")
        assertRejected(overlappingEnd, "layer table end past the data offset")
    }

    @Test
    fun `rejects a segment whose data range runs past the end of the file`() {
        // A two-segment file is outside upstream's three-segment truncation special case.
        val bytes = aixFile(twoSegments()).also { writeIntBe(it, 0x20 + 0x10 + 0x04, 0x1000) }

        assertRejected(bytes, "segment 1 size past EOF")
    }

    @Test
    fun `rejects a truncated header and empty input without throwing`() {
        assertRejected(aixFile(twoSegments()).copyOf(0x10), "truncated header")
        assertRejected(ByteArray(0), "empty input")
    }
}

private fun oneSegment(): List<AixSegment> = listOf(AixSegment(size = 0x100, samples = 44_100, rate = 44_100))

private fun twoSegments(): List<AixSegment> = listOf(
    AixSegment(size = 0x100, samples = 44_100, rate = 44_100),
    AixSegment(size = 0x100, samples = 44_100, rate = 44_100),
)

/** Asserts both the internal parse and the public reader reject [bytes] with a `null`, never a throw. */
private fun assertRejected(bytes: ByteArray, reason: String) {
    assertNull(readAixHeader(bytes), "expected header rejection: $reason")
    assertNull(AixReader(BluntHatchet()).readTracksFromFile(bytes, "music/bad.aix"), "expected reader rejection: $reason")
}

/** Expected headers/samples/rate of one synthetic segment; [size] is its byte length in the file. */
private data class AixSegment(val size: Int, val samples: Int, val rate: Int)

private fun singleTrack(tracks: List<RawTrack>?): RawTrack {
    assertNotNull(tracks)
    assertEquals(1, tracks.size, "expected one track, got ${tracks.size}")
    return tracks.single()
}

// --- Synthetic AIX fixtures (header layout: meta/aix.c `init_vgmstream_aix`) ---------------------

private const val AIX_DATA_OFFSET = 0x800
private const val AIX_RATE = 44_100
private const val AIX_SEGMENT_TABLE_OFFSET = 0x20
private const val AIX_SEGMENT_ENTRY_SIZE = 0x10

/**
 * Builds an AIX file: `AIXF`, data offset/version/header-size dwords, the segment table at 0x20,
 * the 0x01 subtable marker, the layer table, then one zero-filled payload per segment starting at
 * [AIX_DATA_OFFSET]. Layer rates default to 44.1 kHz.
 */
private fun aixFile(
    segments: List<AixSegment>,
    layerRates: List<Int> = listOf(AIX_RATE, AIX_RATE),
    layerCount: Int = layerRates.size,
    subtableMarker: Int = 0x01,
): ByteArray {
    val payloadSize = segments.sumOf { it.size }
    val bytes = ByteArray(AIX_DATA_OFFSET + payloadSize)
    writeFixedAscii(bytes, 0x00, "AIXF", 4)
    writeIntBe(bytes, 0x04, AIX_DATA_OFFSET - 0x08)
    writeIntBe(bytes, 0x08, 0x01000014)
    writeIntBe(bytes, 0x0C, 0x00000800)
    writeShortBe(bytes, 0x18, segments.size)

    var payloadOffset = AIX_DATA_OFFSET
    segments.forEachIndexed { i, segment ->
        val entry = AIX_SEGMENT_TABLE_OFFSET + i * AIX_SEGMENT_ENTRY_SIZE
        writeIntBe(bytes, entry, payloadOffset)
        writeIntBe(bytes, entry + 0x04, segment.size)
        writeIntBe(bytes, entry + 0x08, segment.samples)
        writeIntBe(bytes, entry + 0x0C, segment.rate)
        payloadOffset += segment.size
    }

    val subtableOffset = AIX_SEGMENT_TABLE_OFFSET + segments.size * AIX_SEGMENT_ENTRY_SIZE
    bytes[subtableOffset] = subtableMarker.toByte()
    val layerListOffset = subtableOffset + 0x10
    bytes[layerListOffset] = layerCount.toByte()
    layerRates.forEachIndexed { i, rate ->
        writeIntBe(bytes, layerListOffset + 0x08 + i * 0x08, rate)
    }
    return bytes
}
