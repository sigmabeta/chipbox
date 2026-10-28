package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * Sony VAB (`.vab`) — the PlayStation sound bank that packs many PS-ADPCM waves behind a
 * program/tone table. Ports the metadata layer of vgmstream's `meta/vab.c`, but instead of opening
 * one subsong per file it emits **one [RawTrack] per tone**, so a bank rip appears as individual
 * songs. Each tone's wave is addressed through a 256-entry size table; its sample rate follows from
 * the tone's centre note/fine via the SPU pitch table (`spu1_note_to_pitch` →
 * `spu1_pitch_to_sample_rate`), and its length from the wave size under the same two-loop +
 * ten-second-fade policy as [VagReader].
 *
 * Sidecar `.vab_config` tuning files cannot be read from file bytes, so every tone uses the default
 * note 60 / fine 0 (which is also vgmstream's fallback when no config matches).
 */
class VabReader(private val hatchet: Hatchet) : Reader() {
    override val version: Int = VAB_VERSION

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        try {
            parseBank(bytes, identifier)
        } catch (e: IndexOutOfBoundsException) {
            hatchet.w("VAB parse failed: $identifier has a truncated header (${e.message}).")
            null
        }

    /**
     * Validates the bank header (magic, program count, room for the fixed 0x820-byte program/tone
     * tables plus the wave table), then walks the 128 program slots. A slot with zero tones is
     * padding; a non-empty slot is one program, and its tone entries each become a track.
     */
    private fun parseBank(bytes: ByteArray, identifier: String): List<RawTrack>? {
        val reader = bytesAsReader(bytes)
        val headerReadable = reader.size >= MIN_HEADER_SIZE
        val programs = if (headerReadable) {
            reader.readShortLeAt(PROGRAM_COUNT_OFFSET).toInt() and UINT16_MASK
        } else {
            0
        }
        val wavesOffset = TONES_OFFSET.toLong() + programs.toLong() * TONES_PER_PROGRAM * TONE_SIZE
        val valid = headerReadable &&
            reader.hasAsciiPrefix(VAB_MAGIC) &&
            programs > 0 &&
            wavesOffset + WAVE_TABLE_BYTES <= reader.size
        if (!valid) {
            hatchet.w("VAB parse failed: $identifier is not a supported VAB header.")
            return null
        }

        val tracks = readPrograms(reader, programs, wavesOffset, identifier)
        return if (tracks.isEmpty()) {
            hatchet.w("VAB parse failed: $identifier has no playable tones.")
            null
        } else {
            tracks
        }
    }

    /** Walks the program table, compacting non-empty slots and expanding each program's tones. */
    private fun readPrograms(
        reader: ByteReader,
        programs: Int,
        wavesOffset: Long,
        identifier: String,
    ): List<RawTrack> {
        val tracks = mutableListOf<RawTrack>()
        var subsongIndex = 0
        var programsDone = 0
        var slot = 0

        while (slot < PROGRAM_SLOTS && programsDone < programs) {
            val entryOffset = PROGRAMS_OFFSET + slot * PROGRAM_ENTRY_SIZE
            val toneCount = reader.get(entryOffset).toInt() and 0xFF
            if (toneCount > 0) {
                val programName = readName(reader, entryOffset + PROGRAM_NAME_OFFSET, PROGRAM_NAME_SIZE)
                for (tone in 0 until toneCount) {
                    val track = readTone(
                        reader = reader,
                        wavesOffset = wavesOffset,
                        programNumber = programsDone,
                        toneNumber = tone,
                        title = programName,
                        subsongIndex = subsongIndex,
                        identifier = identifier,
                    )
                    if (track == null) {
                        hatchet.w(
                            "VAB parse skipped: $identifier program $programsDone tone $tone " +
                                "points outside the file.",
                        )
                    } else {
                        tracks += track
                    }
                    subsongIndex++
                }
                programsDone++
            }
            slot++
        }
        return tracks
    }

    /**
     * Resolves one tone's wave through the size table and builds its track. A tone whose entry or
     * wave falls outside this file yields null (and the caller logs it) without failing the bank.
     */
    private fun readTone(
        reader: ByteReader,
        wavesOffset: Long,
        programNumber: Int,
        toneNumber: Int,
        title: String?,
        subsongIndex: Int,
        identifier: String,
    ): RawTrack? {
        val toneOffset = TONES_OFFSET + programNumber * TONES_PER_PROGRAM * TONE_SIZE + toneNumber * TONE_SIZE
        val readable = toneOffset + TONE_SIZE <= reader.size
        val centerNote = if (readable) reader.get(toneOffset + TONE_CENTER_OFFSET).toInt() and 0xFF else 0
        val centerFine = if (readable) reader.get(toneOffset + TONE_FINE_OFFSET).toInt() and 0xFF else 0
        val waveNumber = if (readable) {
            reader.readShortLeAt(toneOffset + TONE_WAVE_OFFSET).toInt() and UINT16_MASK
        } else {
            -1
        }
        val sampleRate = vabSampleRate(centerNote, centerFine)?.takeIf { it > 0 }

        return sampleRate?.let { rate ->
            waveData(reader, wavesOffset, waveNumber)
                ?.takeIf { it.isWithin(reader.size.toLong()) }
                ?.let { wave ->
                    // A zero-sized wave is vgmstream's "empty sound": one second of silence.
                    val lengthMs = if (wave.size == 0L) {
                        EMPTY_SOUND_MS
                    } else {
                        val loop = findPsxLoop(reader, wave.offset.toInt(), wave.size, MONO, NO_INTERLEAVE)
                        psxPlayLengthMs(psBytesToSamples(wave.size, MONO), rate, loop)
                    }
                    streamedRawTrack(
                        identifier = identifier,
                        lengthMs = lengthMs,
                        title = title.orUnknown(),
                        subsongIndex = subsongIndex,
                        platform = Platform.PSX,
                    )
                }
        }
    }

    companion object {
        const val VAB_VERSION = 1
    }
}

/** Absolute data start/length of one wave, derived from the cumulative 8-byte-unit size table. */
private data class WaveData(val offset: Long, val size: Long) {
    fun isWithin(fileSize: Long): Boolean = offset >= 0 && offset <= fileSize && offset + size <= fileSize
}

/** Reads a wave's start/end from the 256-entry table at [wavesOffset]; null when the index is bad. */
private fun waveData(reader: ByteReader, wavesOffset: Long, waveNumber: Int): WaveData? {
    if (waveNumber !in 0 until WAVE_TABLE_ENTRIES) return null
    val table = wavesOffset.toInt()
    var offset = wavesOffset + WAVE_TABLE_BYTES
    for (index in 0 until waveNumber) {
        val entry = reader.readShortLeAt(table + index * 2).toInt() and UINT16_MASK
        offset += entry.toLong() shl 3
    }
    val size = (reader.readShortLeAt(table + waveNumber * 2).toInt() and UINT16_MASK).toLong() shl 3
    return WaveData(offset, size)
}

/**
 * VAB tone centre note/fine → playback rate, via the SPU note→pitch table and
 * `spu1_pitch_to_sample_rate` (`44100 * pitch / 4096`, truncating). Null for a centre note that
 * indexes outside the table, which is a corrupt tone rather than a playable one.
 */
private fun vabSampleRate(centerNote: Int, centerFine: Int): Int? {
    val pitch = noteToPitch(MIDDLE_C, NO_FINE, centerNote, centerFine) ?: return null
    return (SAMPLE_RATE_BASE * pitch / PITCH_FULL_SCALE).toInt()
}

/** Port of `SsPitchFromNote` + the SPU `0x4000` clamp from `util/spu_utils.c`. */
private fun noteToPitch(note: Int, fine: Int, centerNote: Int, centerFine: Int): Int? {
    var shiftedFine = fine + centerFine
    if (shiftedFine < 0) shiftedFine += 7
    shiftedFine = shiftedFine shr 3
    var add = 0
    if (shiftedFine > 15) {
        add = 1
        shiftedFine -= 16
    }
    val calc = add + (note - (centerNote - MIDDLE_C))
    val index = 16 * (calc % 12) + shiftedFine
    if (index !in SVM_PITCH_TABLE.indices) return null

    val type = calc / 12 - 5
    val pitch = when {
        type > 0 -> SVM_PITCH_TABLE[index] shl type
        type < 0 -> SVM_PITCH_TABLE[index] shr -type
        else -> SVM_PITCH_TABLE[index]
    }
    return minOf(pitch, PITCH_MAX)
}

internal const val EXT_VAB = "vab"

private const val VAB_MAGIC = "VABp"
private const val MIN_HEADER_SIZE = 0x14
private const val PROGRAM_COUNT_OFFSET = 0x12
private const val PROGRAMS_OFFSET = 0x20
private const val PROGRAM_SLOTS = 128
private const val PROGRAM_ENTRY_SIZE = 0x10
private const val PROGRAM_NAME_OFFSET = 0x04
private const val PROGRAM_NAME_SIZE = 8
private const val TONES_OFFSET = PROGRAMS_OFFSET + PROGRAM_SLOTS * PROGRAM_ENTRY_SIZE
private const val TONES_PER_PROGRAM = 16
private const val TONE_SIZE = 0x20
private const val TONE_CENTER_OFFSET = 0x04
private const val TONE_FINE_OFFSET = 0x05
private const val TONE_WAVE_OFFSET = 0x16
private const val WAVE_TABLE_ENTRIES = 256
private const val WAVE_TABLE_BYTES = WAVE_TABLE_ENTRIES * 2

private const val UINT16_MASK = 0xFFFF
private const val MONO = 1
private const val NO_INTERLEAVE = 0
private const val EMPTY_SOUND_MS = 1_000L
private const val MIDDLE_C = 60
private const val NO_FINE = 0
private const val PITCH_FULL_SCALE = 4096.0
private const val PITCH_MAX = 0x4000
private const val SAMPLE_RATE_BASE = 44_100.0

/**
 * SPU note→pitch table, `_svm_ptable` in `util/spu_utils.c` (quoted as written upstream; the
 * function only indexes 0..191).
 */
private val SVM_PITCH_TABLE = intArrayOf(
    4096, 4110, 4125, 4140, 4155, 4170, 4185, 4200,
    4216, 4231, 4246, 4261, 4277, 4292, 4308, 4323,
    4339, 4355, 4371, 4386, 4402, 4418, 4434, 4450,
    4466, 4482, 4499, 4515, 4531, 4548, 4564, 4581,
    4597, 4614, 4630, 4647, 4664, 4681, 4698, 4715,
    4732, 4749, 4766, 4783, 4801, 4818, 4835, 4853,
    4870, 4888, 4906, 4924, 4941, 4959, 4977, 4995,
    5013, 5031, 5050, 5068, 5086, 5105, 5123, 5142,
    5160, 5179, 5198, 5216, 5235, 5254, 5273, 5292,
    5311, 5331, 5350, 5369, 5389, 5408, 5428, 5447,
    5467, 5487, 5507, 5527, 5547, 5567, 5587, 5607,
    5627, 5648, 5668, 5688, 5709, 5730, 5750, 5771,
    5792, 5813, 5834, 5855, 5876, 5898, 5919, 5940,
    5962, 5983, 6005, 6027, 6049, 6070, 6092, 6114,
    6137, 6159, 6181, 6203, 6226, 6248, 6271, 6294,
    6316, 6339, 6362, 6385, 6408, 6431, 6455, 6478,
    6501, 6525, 6549, 6572, 6596, 6620, 6644, 6668,
    6692, 6716, 6741, 6765, 6789, 6814, 6839, 6863,
    6888, 6913, 6938, 6963, 6988, 7014, 7039, 7064,
    7090, 7116, 7141, 7167, 7193, 7219, 7245, 7271,
    7298, 7324, 7351, 7377, 7404, 7431, 7458, 7485,
    7512, 7539, 7566, 7593, 7621, 7648, 7676, 7704,
    7732, 7760, 7788, 7816, 7844, 7873, 7901, 7930,
    7958, 7987, 8016, 8045, 8074, 8103, 8133, 8162,
    8192,
)
