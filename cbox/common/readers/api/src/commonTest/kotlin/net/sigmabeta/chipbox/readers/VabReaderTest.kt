package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class VabReaderTest {
    private val reader = VabReader(BluntHatchet())

    @Test
    fun `a two-tone vab bank yields one track per tone`() {
        val bytes = vabFile(programName = "PIANO", waveFrames = listOf(4, 2), waveNumbers = listOf(0, 1))

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "bank.vab"))

        assertEquals(2, tracks.size)
        // 4 PS-ADPCM frames = 112 samples; 112 * 1000 / 44_100 = 2 ms (truncated).
        assertEquals(2L, tracks[0].length)
        // 2 frames = 56 samples; 56 * 1000 / 44_100 = 1 ms.
        assertEquals(1L, tracks[1].length)
        // Streamed readers number subsongs 1-based, matching the native probe.
        assertEquals(listOf(1, 2), tracks.map { it.trackNumber })
        assertEquals("PIANO", tracks[0].title)
        assertEquals("PIANO", tracks[1].title)
        assertEquals(Platform.PSX, tracks[0].platform)
        assertEquals(Platform.PSX, tracks[1].platform)
        assertEquals(0L, tracks[0].fadeLengthMs)
    }

    @Test
    fun `a tone whose wave runs past the file is skipped`() {
        val bytes = vabFile(waveFrames = listOf(4, 2), waveNumbers = listOf(0, 1))
        // Inflate wave 1's size-table entry to 512 * 8 = 0x1000 bytes: its data now leaves the file.
        val waveTable = VAB_TONES_OFFSET + VAB_PROGRAMS * VAB_TONES_PER_PROGRAM * VAB_TONE_SIZE
        writeShortLe(bytes, waveTable + 1 * 2, 512)

        val tracks = assertNotNull(reader.readTracksFromFile(bytes, "bank.vab"))

        // Tone 0 still parses; tone 1 is dropped, and numbering stays with the surviving subsong.
        assertEquals(1, tracks.size)
        assertEquals(1, tracks[0].trackNumber)
        assertEquals(2L, tracks[0].length)
    }

    @Test
    fun `a looping vab tone plays two loops plus the fade`() {
        val bytes = vabFile(waveFrames = listOf(6), waveNumbers = listOf(0))
        // Loop start at frame 0, loop end after frame 3, in the wave's PS-ADPCM data.
        val dataOffset = VAB_TONES_OFFSET + VAB_PROGRAMS * VAB_TONES_PER_PROGRAM * VAB_TONE_SIZE +
            VAB_WAVE_TABLE_BYTES
        bytes[dataOffset + 1] = 0x06
        bytes[dataOffset + 3 * PSX_FRAME_SIZE + 1] = 0x03

        val track = assertNotNull(reader.readTracksFromFile(bytes, "looping.vab")).single()

        // 6 frames = 168 samples; loop 0..112. 0 + 112 * 2 + 10 * 44_100 = 441_224 -> 10_005 ms.
        assertEquals(10_005L, track.length)
    }

    @Test
    fun `a tone's centre note sets its playback rate`() {
        val bytes = vabFile(centerNote = 72, waveFrames = listOf(4), waveNumbers = listOf(0))

        val track = assertNotNull(reader.readTracksFromFile(bytes, "octave.vab")).single()

        // Centre note 72 is one octave up: pitch 8192 >> 1 = 2048 -> 22_050 Hz.
        // 112 samples / 22_050 Hz -> 5 ms (truncated).
        assertEquals(5L, track.length)
    }

    @Test
    fun `the vab extension resolves to the VAB reader at version 1`() {
        val readers = Readers(BluntHatchet())

        assertSame(readers.vab, readers.forExtension("vab"))
        assertEquals(1, readers.readerVersionFor("vab"))
    }
}

/**
 * A one-program VAB whose program 0 contains one tone per entry of [waveNumbers], each pointing at
 * the matching wave in [waveFrames]. Waves are laid out back to back after a full 256-entry size
 * table; every tone uses center note 60 / fine 0, so the reported rate is 44_100 Hz.
 */
internal fun vabFile(
    programName: String = "",
    waveFrames: List<Int> = listOf(4, 2),
    waveNumbers: List<Int> = listOf(0, 1),
    centerNote: Int = 60,
): ByteArray {
    require(waveNumbers.size == waveFrames.size) { "one wave per tone" }
    val waveSizes = waveFrames.map { it * PSX_FRAME_SIZE }
    val wavesOffset = VAB_TONES_OFFSET + VAB_PROGRAMS * VAB_TONES_PER_PROGRAM * VAB_TONE_SIZE
    val dataOffset = wavesOffset + VAB_WAVE_TABLE_BYTES
    val bytes = ByteArray(dataOffset + waveSizes.sum())

    writeFixedAscii(bytes, 0x00, "VABp", 4)
    writeIntLe(bytes, 0x04, 1) // version
    writeIntLe(bytes, 0x08, 0) // VAB id
    writeIntLe(bytes, 0x0C, bytes.size) // total file size
    writeShortLe(bytes, 0x12, VAB_PROGRAMS)
    writeShortLe(bytes, 0x14, waveNumbers.size) // tones
    writeShortLe(bytes, 0x16, waveFrames.size) // waves

    bytes[VAB_PROGRAMS_OFFSET] = waveNumbers.size.toByte()
    writeFixedAscii(bytes, VAB_PROGRAMS_OFFSET + 0x04, programName, 8)

    for ((toneIndex, waveNumber) in waveNumbers.withIndex()) {
        val toneOffset = VAB_TONES_OFFSET + toneIndex * VAB_TONE_SIZE
        bytes[toneOffset + 0x04] = centerNote.toByte() // center note
        bytes[toneOffset + 0x05] = 0 // center fine
        bytes[toneOffset + 0x06] = 0 // min note
        bytes[toneOffset + 0x07] = 127 // max note
        writeShortLe(bytes, toneOffset + 0x16, waveNumber)
    }

    for ((waveIndex, size) in waveSizes.withIndex()) {
        // The table stores each wave's size in 8-byte units.
        writeShortLe(bytes, wavesOffset + waveIndex * 2, size / 8)
    }

    var cursor = dataOffset
    for (frames in waveFrames) {
        for (frame in 0 until frames) {
            val frameOffset = cursor + frame * PSX_FRAME_SIZE
            bytes[frameOffset] = 0x0C
            bytes[frameOffset + 1] = if (frame == frames - 1) 0x01 else 0x00
        }
        cursor += frames * PSX_FRAME_SIZE
    }

    return bytes
}

internal const val VAB_PROGRAMS = 1
internal const val VAB_TONES_PER_PROGRAM = 16
internal const val VAB_TONE_SIZE = 0x20
internal const val VAB_PROGRAMS_OFFSET = 0x20
internal const val VAB_TONES_OFFSET = VAB_PROGRAMS_OFFSET + 128 * 0x10
internal const val VAB_WAVE_TABLE_BYTES = 256 * 2
