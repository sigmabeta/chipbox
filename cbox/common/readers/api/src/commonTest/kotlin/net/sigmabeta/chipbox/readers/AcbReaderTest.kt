package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * CRI ACB cue sheets (`.acb`) with their **embedded** AWB wave bank. Upstream's `meta/acb.c` opens
 * the `AwbFile` column of the `@UTF` header table as a memory wave bank and names each wave from the
 * cue-name → cue → waveform chain; this reader does the same from bytes alone.
 *
 * The bytes-only boundary is part of the contract: an ACB whose `AwbFile` is empty references an
 * external `.awb` that a `Reader` cannot open, so it yields no tracks rather than guessing names or
 * lengths. Fixtures are synthetic (no `.acb` in the local corpus).
 */
class AcbReaderTest {
    private val reader = AcbReader(BluntHatchet())

    @Test
    fun `the acb extension resolves to the ACB reader at version 1`() {
        val readers = Readers(BluntHatchet())

        assertSame(readers.acb, readers.forExtension("acb"))
        assertEquals(1, readers.readerVersionFor("acb"))
    }

    @Test
    fun `an embedded bank with two named cues yields literal titles and lengths`() {
        val bank = awb(
            listOf(
                AwbCueFixture(waveId = 10, audio = hcaCue(blockCount = 44)),
                AwbCueFixture(waveId = 11, audio = hcaCue(blockCount = 88)),
            ),
        )
        val bytes = acb(
            awb = bank,
            cues = listOf(
                AcbCueFixture(name = "Stage 1", waveId = 10),
                AcbCueFixture(name = "Boss", waveId = 11),
            ),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "sound/bgm.acb"))

        assertEquals(listOf("Stage 1", "Boss"), tracks.map { it.title })
        // 44 blocks * 1024 = 45_056 samples -> 1021 ms; 88 blocks * 1024 = 90_112 -> 2043 ms.
        assertEquals(listOf(1_021L, 2_043L), tracks.map { it.length })
        assertEquals(listOf(1, 2), tracks.map { it.trackNumber })
        assertEquals(listOf(0L, 0L), tracks.map { it.fadeLengthMs })
        assertEquals(listOf(Platform.OTHER, Platform.OTHER), tracks.map { it.platform })
        assertEquals(listOf("sound/bgm.acb", "sound/bgm.acb"), tracks.map { it.path })
        assertEquals(listOf(TAG_UNKNOWN, TAG_UNKNOWN), tracks.map { it.artist })
    }

    @Test
    fun `duplicate cue names fall back to the unknown sentinel on every wave`() {
        val bank = awb(
            listOf(
                AwbCueFixture(waveId = 1, audio = hcaCue(blockCount = 44)),
                AwbCueFixture(waveId = 2, audio = hcaCue(blockCount = 44)),
            ),
        )
        val bytes = acb(
            awb = bank,
            cues = listOf(
                AcbCueFixture(name = "Same", waveId = 1),
                AcbCueFixture(name = "Same", waveId = 2),
            ),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "sound/dupes.acb"))

        assertEquals(listOf(TAG_UNKNOWN, TAG_UNKNOWN), tracks.map { it.title })
        assertEquals(listOf(1_021L, 1_021L), tracks.map { it.length })
    }

    @Test
    fun `an empty cue name is the unknown sentinel and does not disturb its neighbour`() {
        val bank = awb(
            listOf(
                AwbCueFixture(waveId = 1, audio = hcaCue(blockCount = 44)),
                AwbCueFixture(waveId = 2, audio = hcaCue(blockCount = 44)),
            ),
        )
        val bytes = acb(
            awb = bank,
            cues = listOf(
                AcbCueFixture(name = "", waveId = 1),
                AcbCueFixture(name = "Boss", waveId = 2),
            ),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "sound/blank.acb"))

        assertEquals(listOf(TAG_UNKNOWN, "Boss"), tracks.map { it.title })
    }

    @Test
    fun `a cue chain this reader does not follow leaves the wave unnamed`() {
        // ReferenceType 2 is Cue > Synth > Waveform; the name chain requires the Synth table, which
        // is out of scope. The wave is still emitted (its length is readable), just with no title.
        val bank = awb(listOf(AwbCueFixture(waveId = 1, audio = hcaCue(blockCount = 44))))
        val bytes = acb(
            awb = bank,
            cues = listOf(AcbCueFixture(name = "Synth Cue", waveId = 1, referenceType = 2)),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "sound/synth.acb"))

        assertEquals(listOf(TAG_UNKNOWN), tracks.map { it.title })
        assertEquals(listOf(1_021L), tracks.map { it.length })
    }

    @Test
    fun `a cue name pointing outside the cue table leaves the wave unnamed`() {
        val bank = awb(listOf(AwbCueFixture(waveId = 1, audio = hcaCue(blockCount = 44))))
        val bytes = acb(
            awb = bank,
            cues = listOf(AcbCueFixture(name = "Dangling", waveId = 1, cueIndex = 99)),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "sound/dangling.acb"))

        assertEquals(listOf(TAG_UNKNOWN), tracks.map { it.title })
        assertEquals(listOf(1_021L), tracks.map { it.length })
    }

    @Test
    fun `an ACB that references an external AWB yields no tracks`() {
        val bytes = acb(
            awb = null,
            cues = listOf(AcbCueFixture(name = "External", waveId = 1)),
        )

        // An empty AwbFile is upstream's "bank has no subsongs (ignore)" case: recognised, no tracks.
        assertEquals(emptyList(), reader.readTracksFromFile(bytes, "sound/external.acb"))
    }

    @Test
    fun `an unreadable or encrypted cue table yields no tracks instead of throwing`() {
        val valid = acb(
            awb = awb(listOf(AwbCueFixture(waveId = 1, audio = hcaCue(blockCount = 44)))),
            cues = listOf(AcbCueFixture(name = "Stage 1", waveId = 1)),
        )

        // A declared table size that disagrees with the file (encrypted/obfuscated header) is skipped.
        val wrongSize = valid.copyOf()
        writeIntBe(wrongSize, 0x04, valid.size)
        assertEquals(emptyList(), reader.readTracksFromFile(wrongSize, "sound/encrypted.acb"))

        // A truncated table is a recognised ACB that cannot be read, not a scan failure.
        for (length in listOf(0x10, 0x20, 0x40)) {
            assertEquals(
                emptyList(),
                reader.readTracksFromFile(valid.copyOf(minOf(length, valid.size)), "sound/truncated.acb"),
                "truncated to $length",
            )
        }
    }

    @Test
    fun `a file without the UTF magic is not an ACB`() {
        val bytes = acb(null, emptyList())
        writeFixedAscii(bytes, 0x00, "@XML", 4)

        assertNull(reader.readTracksFromFile(bytes, "not-a-cue-sheet.acb"))
    }

    @Test
    fun `an embedded bank with a skipped cue keeps the surviving cue's wave number`() {
        val bank = awb(
            listOf(
                AwbCueFixture(waveId = 1, audio = ByteArray(0x40) { 0x5A }),
                AwbCueFixture(waveId = 2, audio = hcaCue(blockCount = 44)),
            ),
        )
        val bytes = acb(
            awb = bank,
            cues = listOf(
                AcbCueFixture(name = "Broken", waveId = 1),
                AcbCueFixture(name = "Boss", waveId = 2),
            ),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "sound/partial.acb"))

        assertEquals(listOf("Boss"), tracks.map { it.title })
        assertEquals(listOf(2), tracks.map { it.trackNumber })
    }
}
