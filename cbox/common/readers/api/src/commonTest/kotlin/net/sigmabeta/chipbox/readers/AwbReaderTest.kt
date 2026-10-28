package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * CRI AFS2/AWB wave banks (`meta/awb.c`). Every fixture is synthetic — the local corpus has no
 * `.awb`/`.afs2` file — and each literal below is derived from the upstream arithmetic, not from
 * this reader's output.
 */
class AwbReaderTest {
    private val reader = AwbReader(BluntHatchet())

    @Test
    fun `awb and afs2 resolve to the AWB reader at version 1`() {
        val readers = Readers(BluntHatchet())

        assertSame(readers.awb, readers.forExtension("awb"))
        assertSame(readers.awb, readers.forExtension("afs2"))
        assertEquals(1, readers.readerVersionFor("awb"))
        assertEquals(1, readers.readerVersionFor("afs2"))
    }

    @Test
    fun `a bank with two HCA cues yields one track per cue with one-based numbers`() {
        val bytes = awb(
            listOf(
                AwbCueFixture(waveId = 10, audio = hcaCue(blockCount = 44)),
                AwbCueFixture(waveId = 11, audio = hcaCue(blockCount = 88)),
            ),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "stream/voices.awb"))

        assertEquals(2, tracks.size)
        // 44 blocks * 1024 samples = 45_056; 45_056 * 1000 / 44_100 = 1021 ms (truncating).
        assertEquals(1_021L, tracks[0].length)
        // 88 blocks * 1024 = 90_112 samples; 90_112 * 1000 / 44_100 = 2043 ms.
        assertEquals(2_043L, tracks[1].length)
        assertEquals(listOf(1, 2), tracks.map { it.trackNumber })
        // AWB carries wave ids, not names: the scanner's filename fallback applies only because a
        // one-track result is unknown-titled, so the reader's own title is the sentinel.
        assertEquals(listOf(TAG_UNKNOWN, TAG_UNKNOWN), tracks.map { it.title })
        assertEquals(listOf(0L, 0L), tracks.map { it.fadeLengthMs })
        assertEquals(listOf(Platform.OTHER, Platform.OTHER), tracks.map { it.platform })
        assertEquals(listOf("stream/voices.awb", "stream/voices.awb"), tracks.map { it.path })
    }

    @Test
    fun `an ADX cue uses the ADX length path`() {
        val bytes = awb(
            listOf(
                AwbCueFixture(waveId = 0, audio = hcaCue(blockCount = 44)),
                AwbCueFixture(waveId = 1, audio = adxCue(numSamples = 44_100)),
            ),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "stream/bank.awb"))

        assertEquals(2, tracks.size)
        assertEquals(1_021L, tracks[0].length)
        // 44_100 samples / 44_100 Hz * 1000 = 1000 ms (a non-looping ADX cue).
        assertEquals(1_000L, tracks[1].length)
        assertEquals(listOf(1, 2), tracks.map { it.trackNumber })
    }

    @Test
    fun `a small bank with 16-bit offset-table entries parses the same way`() {
        val bytes = awb(
            listOf(
                AwbCueFixture(waveId = 0, audio = hcaCue(blockCount = 44)),
                AwbCueFixture(waveId = 1, audio = hcaCue(blockCount = 88)),
            ),
            offsetSize = 2,
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "stream/small.awb"))

        assertEquals(listOf(1_021L, 2_043L), tracks.map { it.length })
        assertEquals(listOf(1, 2), tracks.map { it.trackNumber })
    }

    @Test
    fun `a cue with an invalid offset range is skipped`() {
        val bytes = awb(
            listOf(
                AwbCueFixture(waveId = 0, audio = hcaCue(blockCount = 44)),
                AwbCueFixture(waveId = 1, audio = hcaCue(blockCount = 88)),
            ),
        )
        // Offset table starts after the 0x10 header and the 2-entry id table. The last entry is the
        // bank end and doubles as the second cue's end; point it before that cue's start.
        writeIntLe(bytes, 0x14 + 8, 0)

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "stream/bad-offset.awb"))

        assertEquals(listOf(1_021L), tracks.map { it.length })
        assertEquals(listOf(1), tracks.map { it.trackNumber })
    }

    @Test
    fun `a cue with an unsupported codec is skipped without shifting the other cue numbers`() {
        val bytes = awb(
            listOf(
                AwbCueFixture(waveId = 0, audio = ByteArray(0x40) { 0x5A }),
                AwbCueFixture(waveId = 1, audio = hcaCue(blockCount = 44)),
            ),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "stream/mixed.awb"))

        assertEquals(1, tracks.size)
        // The surviving cue keeps the wave index (2), so rescan identities stay aligned with the
        // native probe's subsong numbers.
        assertEquals(2, tracks.single().trackNumber)
        assertEquals(1_021L, tracks.single().length)
    }

    @Test
    fun `a bank with an unreadable header yields no tracks instead of throwing`() {
        val header = ByteArray(0x40)
        writeFixedAscii(header, 0x00, "AFS2", 4)
        writeIntLe(header, 0x08, 2)
        // offset_size 3 is not one of the two widths upstream accepts.
        header[0x05] = 3
        assertEquals(emptyList(), reader.readTracksFromFile(header, "broken.awb"))

        // A declared count whose offset table runs past the file is skipped, not guessed at.
        val truncated = ByteArray(0x20)
        writeFixedAscii(truncated, 0x00, "AFS2", 4)
        truncated[0x05] = 4
        writeShortLe(truncated, 0x06, 2)
        writeIntLe(truncated, 0x08, 10_000)
        writeShortLe(truncated, 0x0C, 4)
        assertEquals(emptyList(), reader.readTracksFromFile(truncated, "broken.awb"))
    }

    @Test
    fun `a file without the AFS2 magic is not an AWB`() {
        for (magic in listOf("AFS1", "afs2", "AFS ")) {
            val bytes = awb(listOf(AwbCueFixture(0, hcaCue())))
            writeFixedAscii(bytes, 0x00, magic, 4)

            assertNull(reader.readTracksFromFile(bytes, "not-a-bank.awb"), "magic '$magic'")
        }
    }

    @Test
    fun `an empty bank yields no tracks`() {
        val bytes = awb(emptyList())

        assertEquals(emptyList(), reader.readTracksFromFile(bytes, "empty.awb"))
    }
}
