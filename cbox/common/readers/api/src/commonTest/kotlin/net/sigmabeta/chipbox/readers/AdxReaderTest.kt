package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class AdxReaderTest {
    private val reader = AdxReader(BluntHatchet())

    @Test
    fun `the adx extension resolves to the ADX reader at version 1`() {
        val readers = Readers(BluntHatchet())

        assertSame(readers.adx, readers.forExtension("adx"))
        assertEquals(1, readers.readerVersionFor("adx"))
    }

    @Test
    fun `a v0x0300 header without loop info reports its sample count`() {
        val bytes = adxFixture(version = VERSION_0300, numSamples = 44_100)

        val track = singleTrack(reader.readTracksFromFile(bytes, "Grandia.adx"))

        // 44_100 samples / 44_100 Hz * 1000 = 1000 ms (integer division, no rounding)
        assertEquals(1_000L, track.length)
    }

    @Test
    fun `a looping v0x0300 header reads loop points at its fixed offset`() {
        // v0x0300 has no sample history, so the loop block always starts at 0x14.
        val bytes = adxFixture(
            version = VERSION_0300,
            numSamples = 44_100,
            loop = LoopPoints(start = 4_410, end = 44_100),
        )

        val track = singleTrack(reader.readTracksFromFile(bytes, "Grandia.adx"))

        // 4_410 + (44_100 - 4_410) * 2 + 10 * 44_100 = 524_790 samples -> 11_900 ms
        assertEquals(11_900L, track.length)
    }

    @Test
    fun `a v0x0300 header too short for loop info reports its sample count`() {
        // startOffset - 6 = 0x2a is smaller than the 0x2c needed for the 0x14 + 0x18 loop block.
        val bytes = adxFixture(
            version = VERSION_0300,
            numSamples = 44_100,
            startOffset = 0x30,
            loop = LoopPoints(start = 4_410, end = 44_100),
        )

        val track = singleTrack(reader.readTracksFromFile(bytes, "Grandia.adx"))

        assertEquals(1_000L, track.length)
    }

    @Test
    fun `a v0x0400 header without loop info reports its sample count`() {
        val bytes = adxFixture(version = VERSION_0400, numSamples = 44_100)

        val track = singleTrack(reader.readTracksFromFile(bytes, "movie.adx"))

        assertEquals(1_000L, track.length)
    }

    @Test
    fun `a looping v0x0400 header plays two loops plus a ten second fade`() {
        // History is 4 * channels bytes at 0x18, so loop info starts at 0x18 + 8 = 0x20.
        val bytes = adxFixture(
            version = VERSION_0400,
            numSamples = 44_100,
            loop = LoopPoints(start = 4_410, end = 44_100),
        )

        val track = singleTrack(reader.readTracksFromFile(bytes, "movie.adx"))

        // 4_410 + (44_100 - 4_410) * 2 + 10 * 44_100 = 524_790 samples
        // 524_790 / 44_100 = 11.9 s = 11_900 ms
        assertEquals(11_900L, track.length)
    }

    @Test
    fun `a v0x0400 header skips AINF when its size leaves no room for loop points`() {
        // The AINF block starts 4 bytes past the history and is large enough that the loop-info
        // space check only passes if its size is ignored.
        val bytes = adxFixture(
            version = VERSION_0400,
            numSamples = 44_100,
            ainfSize = 0xC8,
        )

        val track = singleTrack(reader.readTracksFromFile(bytes, "movie.adx"))

        // Ignoring AINF would read the bogus loop at 0x20 and produce a different length.
        assertEquals(1_000L, track.length)
    }

    @Test
    fun `a v0x0500 header has no loop info and reports its sample count`() {
        // Loop-shaped bytes are present where a v0x0400 header would keep them; v0x0500 must ignore.
        val bytes = adxFixture(
            version = VERSION_0500,
            numSamples = 44_100,
            loop = LoopPoints(start = 4_410, end = 44_100),
        )

        val track = singleTrack(reader.readTracksFromFile(bytes, "movie_a.adx"))

        assertEquals(1_000L, track.length)
    }

    @Test
    fun `encrypted v0x0408 and v0x0409 headers parse as the v0x0400 layout without a key`() {
        for (encryptedVersion in listOf(VERSION_0408, VERSION_0409)) {
            val bytes = adxFixture(
                version = encryptedVersion,
                numSamples = 44_100,
                loop = LoopPoints(start = 4_410, end = 44_100),
            )

            val track = singleTrack(reader.readTracksFromFile(bytes, "encrypted.adx"))

            // Same 4_410 + 39_690 * 2 + 441_000 = 524_790 samples as the plain v0x0400 fixture.
            assertEquals(11_900L, track.length, "version=0x${encryptedVersion.toString(16)}")
        }
    }

    @Test
    fun `length millisecond math truncates rather than rounds`() {
        // 44_144 samples * 1000 / 44_100 = 1000.997… -> 1000 ms.
        val bytes = adxFixture(version = VERSION_0400, numSamples = 44_144)

        val track = singleTrack(reader.readTracksFromFile(bytes, "truncate.adx"))

        assertEquals(1_000L, track.length)
    }

    @Test
    fun `the parsed track follows the streamed-audio conventions`() {
        val bytes = adxFixture(version = VERSION_0400)

        val track = singleTrack(reader.readTracksFromFile(bytes, "Meteos/001 BGM.adx"))

        assertEquals("Meteos/001 BGM.adx", track.path)
        // ADX carries no stream name, so the scanner's filename fallback must supply the title.
        assertEquals(TAG_UNKNOWN, track.title)
        assertEquals(TAG_UNKNOWN, track.artist)
        assertEquals(TAG_UNKNOWN, track.game)
        assertEquals(1, track.trackNumber)
        assertEquals(0L, track.fadeLengthMs)
        assertEquals(Platform.OTHER, track.platform)
    }

    @Test
    fun `the header helper reads an embedded stream at an offset`() {
        val embedded = adxFixture(
            version = VERSION_0400,
            numSamples = 44_100,
            loop = LoopPoints(start = 4_410, end = 44_100),
        )
        val container = cat(ByteArray(0x123) { 0x5A }, embedded, ByteArray(0x10))

        // Not at offset 0: nothing parses.
        assertNull(readAdxHeader(container, 0))

        // The reusable helper (ticket 09) parses an ADX cue embedded in an AWB.
        val header = assertNotNull(readAdxHeader(container, 0x123))
        // Same 4_410 + (44_100 - 4_410) * 2 + 10 * 44_100 = 524_790 samples -> 11_900 ms.
        assertEquals(11_900L, header.lengthMs)
    }

    @Test
    fun `an out-of-range ADX offset is never a header`() {
        val bytes = adxFixture(version = VERSION_0400)

        assertNull(readAdxHeader(bytes, -1))
        assertNull(readAdxHeader(bytes, bytes.size))
        assertNull(readAdxHeader(bytes, bytes.size + 0x100))
    }

    @Test
    fun `a file whose signature is not 0x8000 yields no tracks`() {
        val bytes = adxFixture(version = VERSION_0400).also { writeShortBe(it, 0x00, 0x8001) }

        assertNull(reader.readTracksFromFile(bytes, "bad-signature.adx"))
    }

    @Test
    fun `a missing (c)CRI marker yields no tracks`() {
        val bytes = adxFixture(version = VERSION_0400).also {
            writeFixedAscii(it, DATA_START - 6, "(c)XRI", 6)
        }

        assertNull(reader.readTracksFromFile(bytes, "bad-magic.adx"))
    }

    @Test
    fun `an unsupported encoding type yields no tracks`() {
        for (encodingType in listOf(0x00, 0x01, 0x05, 0x10)) {
            val bytes = adxFixture(version = VERSION_0400, encodingType = encodingType)

            assertNull(
                reader.readTracksFromFile(bytes, "encoding.adx"),
                "encoding=0x${encodingType.toString(16)}",
            )
        }
    }

    @Test
    fun `an unsupported frame size yields no tracks`() {
        val bytes = adxFixture(version = VERSION_0400, frameSize = 0x11)

        assertNull(reader.readTracksFromFile(bytes, "frame.adx"))
    }

    @Test
    fun `an unsupported bits-per-sample yields no tracks`() {
        val bytes = adxFixture(version = VERSION_0400, bitsPerSample = 8)

        assertNull(reader.readTracksFromFile(bytes, "bits.adx"))
    }

    @Test
    fun `channel counts outside one to eight yield no tracks`() {
        for (channels in listOf(0, 9)) {
            val bytes = adxFixture(version = VERSION_0400, channels = channels)

            assertNull(reader.readTracksFromFile(bytes, "channels.adx"), "channels=$channels")
        }
    }

    @Test
    fun `a non-positive sample rate or negative sample count yields no tracks`() {
        for (sampleRate in listOf(0, -44_100)) {
            val silent = adxFixture(version = VERSION_0400, sampleRate = sampleRate)
            assertNull(reader.readTracksFromFile(silent, "rate.adx"), "rate=$sampleRate")
        }

        val negativeSamples = adxFixture(version = VERSION_0400, numSamples = -1)
        assertNull(reader.readTracksFromFile(negativeSamples, "samples-negative.adx"))
    }

    @Test
    fun `an unknown version signature yields no tracks`() {
        val bytes = adxFixture(version = 0x0600)

        assertNull(reader.readTracksFromFile(bytes, "version.adx"))
    }

    @Test
    fun `a truncated or impossible header yields no tracks without throwing`() {
        val valid = adxFixture(version = VERSION_0400)
        val truncated = listOf(
            ByteArray(0),
            ByteArray(0x10),
            valid.copyOf(0x14),
            // Header size field points past the end of the file.
            valid.copyOf(0x40).also { writeShortBe(it, 0x02, 0xFFFC) },
            // Start offset smaller than the fixed header — `(c)CRI` cannot sit before it.
            valid.also { writeShortBe(it, 0x02, 0) },
        )

        for (bytes in truncated) {
            assertNull(reader.readTracksFromFile(bytes, "truncated.adx"))
        }
    }
}

/** One parsed track, asserted to exist exactly once. */
private fun singleTrack(tracks: List<RawTrack>?): RawTrack {
    assertNotNull(tracks)
    assertEquals(1, tracks.size, "expected one track, got ${tracks.size}")
    return tracks.single()
}

private const val VERSION_0300 = 0x0300
private const val VERSION_0400 = 0x0400
private const val VERSION_0408 = 0x0408
private const val VERSION_0409 = 0x0409
private const val VERSION_0500 = 0x0500

/** Loop points as stored in the header's optional loop block. */
private data class LoopPoints(val start: Int, val end: Int)

/** Where a fixture's audio payload begins; also where `(c)CRI` sits at `startOffset - 6`. */
private const val DATA_START = 0x100

/**
 * A minimal, structurally valid ADX header followed by zeroed "audio" bytes. Every field a reader
 * validates is settable so negative tests can corrupt exactly one thing.
 */
private fun adxFixture(
    version: Int,
    channels: Int = 2,
    sampleRate: Int = 44_100,
    numSamples: Int = 44_100,
    encodingType: Int = 3,
    frameSize: Int = 0x12,
    bitsPerSample: Int = 4,
    startOffset: Int = DATA_START,
    loop: LoopPoints? = null,
    ainfSize: Int? = null,
): ByteArray = ByteArray(startOffset + 0x20).also { bytes ->
    writeShortBe(bytes, 0x00, 0x8000)
    writeShortBe(bytes, 0x02, startOffset - 4)
    bytes[0x04] = encodingType.toByte()
    bytes[0x05] = frameSize.toByte()
    bytes[0x06] = bitsPerSample.toByte()
    bytes[0x07] = channels.toByte()
    writeIntBe(bytes, 0x08, sampleRate)
    writeIntBe(bytes, 0x0C, numSamples)
    writeShortBe(bytes, 0x10, 500)
    writeShortBe(bytes, 0x12, version)
    writeFixedAscii(bytes, startOffset - 6, "(c)CRI", 6)

    if (version != VERSION_0300) {
        ainfSize?.let { size ->
            val ainfOffset = HISTORY_OFFSET + historySize(channels) + 4
            writeFixedAscii(bytes, ainfOffset, "AINF", 4)
            writeIntBe(bytes, ainfOffset + 4, size)
        }
    }
    loop?.let { points ->
        val loopsOffset = if (version == VERSION_0300) {
            LEGACY_LOOPS_OFFSET
        } else {
            HISTORY_OFFSET + historySize(channels)
        }
        writeIntBe(bytes, loopsOffset + 0x04, 1)
        writeIntBe(bytes, loopsOffset + 0x08, points.start)
        writeIntBe(bytes, loopsOffset + 0x10, points.end)
    }
}

/** v0x0400's per-channel sample history: 4 bytes per channel, minimum 8 bytes. */
private fun historySize(channels: Int): Int = if (channels > 1) 4 * channels else 8

private const val HISTORY_OFFSET = 0x18
private const val LEGACY_LOOPS_OFFSET = 0x14
