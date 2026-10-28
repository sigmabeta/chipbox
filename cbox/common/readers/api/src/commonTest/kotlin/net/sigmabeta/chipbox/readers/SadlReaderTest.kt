package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * SADL (`cbox/native/vgmstream/Core/vgmstream/src/meta/sadl.c`) — streams from DS games using
 * Procyon Studio's audio driver (Professor Layton, Soma Bringer).
 *
 * The `flags` byte at 0x33 carries both the sample rate (`flags & 6`: 4 -> 32 728 Hz,
 * 2/0 -> 16 364 Hz) and the codec branch (`flags & 0xF0`: 0x00/0x70 IMA, 0xB0 NDS Procyon).
 * The byte at 0x31 is the loop flag. Expected lengths are the native probe's
 * `play_samples * 1000 / sample_rate`; the corpus-backed case was confirmed against the
 * pre-cutover database export (stored = reader length + 2 000 ms zero-fade padding).
 */
class SadlReaderTest {

    private val reader = SadlReader(BluntHatchet())

    @Test
    fun `the corpus IMA stream matches the probe length`() {
        // Corpus: "01 Professor Layton's Theme.sad" — stereo, flags 0x72 (IMA, 16 364 Hz),
        // data 0x100..2 107 424. dataBytes = 2 107 168; samples = 2 107 168 * 2 / 2 = 2 107 168
        // (probe's play_samples); / 16 364 = 128 768 ms; DB 130 768.
        val track = singleTrack(
            sadlFile(
                channels = 2,
                flags = 0x72,
                loop = false,
                dataSize = 2_107_424,
                startOffset = 0x100,
                loopStartBytes = 0x100,
            ),
        )

        assertEquals(128_768L, track.length)
        assertEquals(0L, track.fadeLengthMs)
    }

    @Test
    fun `a looping IMA stream uses the loop start byte offset`() {
        // Mono IMA (flags 0x70 -> 16 364 Hz). dataBytes = 4 096 -> 8 192 samples;
        // loopStartBytes = 1 024 -> 2 048 samples.
        // play = 2 048 + (8 192 - 2 048) * 2 + 163 640 = 177 976 -> 10 876 ms.
        val track = singleTrack(
            sadlFile(
                channels = 1,
                flags = 0x70,
                loop = true,
                dataSize = 0x100 + 4_096,
                startOffset = 0x100,
                loopStartBytes = 0x100 + 1_024,
            ),
        )

        assertEquals(10_876L, track.length)
    }

    @Test
    fun `the 0x00 flags branch is also IMA`() {
        // flags & 6 == 2 -> 16 364 Hz; flags & 0xF0 == 0x00 -> coding_IMA_mono.
        // dataBytes 2 048 mono -> 4 096 samples -> 250 ms.
        val track = singleTrack(
            sadlFile(
                channels = 1,
                flags = 0x02,
                loop = false,
                dataSize = 0x100 + 2_048,
                startOffset = 0x100,
                loopStartBytes = 0x100,
            ),
        )

        assertEquals(250L, track.length)
    }

    @Test
    fun `the NDS Procyon branch decodes 30 samples per 16-byte block per channel`() {
        // flags & 6 == 4 -> 32 728 Hz; flags & 0xF0 == 0xB0 -> coding_NDS_PROCYON.
        // dataBytes 8 192 stereo -> 8 192 / 2 / 16 * 30 = 7 680 samples -> 234 ms.
        val track = singleTrack(
            sadlFile(
                channels = 2,
                flags = 0xb4,
                loop = false,
                dataSize = 0x100 + 8_192,
                startOffset = 0x100,
                loopStartBytes = 0x100,
            ),
        )

        assertEquals(234L, track.length)
    }

    @Test
    fun `streamed tracks use the 1-based convention, unknown tags and OTHER platform`() {
        val track = singleTrack(
            sadlFile(
                channels = 1,
                flags = 0x70,
                loop = false,
                dataSize = 0x100 + 2_048,
                startOffset = 0x100,
                loopStartBytes = 0x100,
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
    fun `rejects a file without the sadl magic`() {
        val bytes = sadlFile(
            channels = 1,
            flags = 0x70,
            loop = false,
            dataSize = 0x100 + 2_048,
            startOffset = 0x100,
            loopStartBytes = 0x100,
            magic = "NOPE",
        )

        assertNull(reader.readTracksFromFile(bytes, "bogus.sad"))
    }

    @Test
    fun `rejects an unknown codec branch and unknown rate bits`() {
        // 0x10 is neither IMA (0x00/0x70) nor NDS Procyon (0xB0).
        assertNull(
            reader.readTracksFromFile(
                sadlFile(
                    channels = 1,
                    flags = 0x10,
                    loop = false,
                    dataSize = 0x100 + 2_048,
                    startOffset = 0x100,
                    loopStartBytes = 0x100,
                ),
                "bogus.sad",
            ),
        )
        // flags & 6 == 6 is not a known sample rate.
        assertNull(
            reader.readTracksFromFile(
                sadlFile(
                    channels = 1,
                    flags = 0x76,
                    loop = false,
                    dataSize = 0x100 + 2_048,
                    startOffset = 0x100,
                    loopStartBytes = 0x100,
                ),
                "bogus.sad",
            ),
        )
    }

    @Test
    fun `rejects zero channels and a data size below the data offset`() {
        assertNull(
            reader.readTracksFromFile(
                sadlFile(
                    channels = 0,
                    flags = 0x70,
                    loop = false,
                    dataSize = 0x100 + 2_048,
                    startOffset = 0x100,
                    loopStartBytes = 0x100,
                ),
                "bogus.sad",
            ),
        )
        assertNull(
            reader.readTracksFromFile(
                sadlFile(
                    channels = 1,
                    flags = 0x70,
                    loop = false,
                    dataSize = 0x80,
                    startOffset = 0x100,
                    loopStartBytes = 0x100,
                ),
                "bogus.sad",
            ),
        )
    }

    @Test
    fun `rejects a truncated header without throwing`() {
        val bytes = sadlFile(
            channels = 1,
            flags = 0x70,
            loop = false,
            dataSize = 0x100 + 2_048,
            startOffset = 0x100,
            loopStartBytes = 0x100,
            fileSize = 0x40,
        )

        assertNull(reader.readTracksFromFile(bytes, "truncated.sad"))
    }

    private fun singleTrack(bytes: ByteArray): RawTrack {
        val tracks = reader.readTracksFromFile(bytes, "test.sad")
        assertNotNull(tracks)
        assertEquals(1, tracks.size)
        return tracks[0]
    }
}

/**
 * Minimal SADL header: the "sadl" magic, loop flag at 0x31, channels at 0x32, rate/codec flags at
 * 0x33, and the data/loop byte offsets at 0x40/0x48/0x54 — the fields `sadl.c` reads. The payload
 * is zero-filled; the reader derives its sample count from the header's byte counts.
 */
private fun sadlFile(
    channels: Int,
    flags: Int,
    dataSize: Int,
    startOffset: Int,
    loopStartBytes: Int,
    loop: Boolean = false,
    magic: String = "sadl",
    fileSize: Int = 0x100,
): ByteArray {
    val bytes = ByteArray(maxOf(fileSize, 0x58))
    writeFixedAscii(bytes, 0x00, magic, 4)
    bytes[0x31] = if (loop) 1 else 0
    bytes[0x32] = channels.toByte()
    bytes[0x33] = flags.toByte()
    writeIntLe(bytes, 0x40, dataSize)
    writeIntLe(bytes, 0x48, startOffset)
    writeIntLe(bytes, 0x54, loopStartBytes)
    return bytes.copyOf(fileSize)
}
