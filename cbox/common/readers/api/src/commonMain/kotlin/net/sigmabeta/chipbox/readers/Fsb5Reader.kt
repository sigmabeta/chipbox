package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * FSB5 — Firelight's FMOD Studio sample bank (`.fsb` / `.fsb5`), one [RawTrack] per subsong.
 *
 * The layout mirrors `cbox/native/vgmstream/Core/vgmstream/src/meta/fsb5.c`:
 * - a fixed header (`FSB5`, version, subsong count, the three section sizes, codec id, and for
 *   version 1 the mode/flags word), whose section sizes must add up to the file length exactly;
 * - one variable-length sample header per subsong: a packed sample-mode `u64` (sample count, data
 *   offset, channel code, sample-rate code, "has extra flags" bit) followed by an optional chain of
 *   extra flags, of which only loop info (type `0x03`) changes the metadata;
 * - a name table of `u32` offsets (one per subsong, relative to the table start) followed by
 *   NUL-terminated strings;
 * - the sample-data section.
 *
 * Length is what the native probe reports (`play_samples` under the shared config
 * `loop_count = 2.0`, `fade_time = 10.0`): a looped subsong plays
 * `loop_start + (loop_end - loop_start) * 2 + 10 * sample_rate` samples, an unlooped one just
 * `num_samples`, and milliseconds are `samples * 1000 / rate` in [Long] arithmetic. Streamed tracks
 * keep `fadeLengthMs = 0`; the scanner's existing zero-fade padding is unchanged.
 *
 * Failure modes follow the module convention: a structurally corrupt bank (bad magic/version,
 * wrong size, sample-table or name-table overrun, truncated file) logs at `w` and returns `null`,
 * while a bank whose codec [StreamedCodecs] does not allow is *recognised but unsupported* and
 * returns an empty list (`emptyList()`), so a scan never advertises audio the build cannot decode.
 * Never throws.
 *
 * The bank carries no console information, so every track is [Platform.OTHER]; the reader never
 * infers a platform from [identifier] (a content-source id need not have a file extension).
 */
class Fsb5Reader(private val hatchet: Hatchet) : Reader() {

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        try {
            readTracks(bytes, identifier)
        } catch (e: Fsb5CorruptException) {
            hatchet.w("FSB5 parse failed for $identifier: ${e.message}")
            null
        }

    private fun readTracks(bytes: ByteArray, identifier: String): List<RawTrack> {
        val reader = bytesAsReader(bytes)
        if (!reader.hasAsciiPrefix(MAGIC)) throw Fsb5CorruptException("missing FSB5 magic")

        val version = reader.u32le(OFFSET_VERSION) ?: throw Fsb5CorruptException("truncated header")
        if (version != VERSION_0 && version != VERSION_1) {
            throw Fsb5CorruptException("unsupported version $version")
        }
        val baseHeaderSize = if (version == VERSION_1) BASE_HEADER_SIZE_V1 else BASE_HEADER_SIZE_V0
        val subsongCount = reader.u32le(OFFSET_SUBSONGS) ?: throw Fsb5CorruptException("truncated header")
        if (subsongCount !in 1..MAX_SUBSONGS) {
            throw Fsb5CorruptException("implausible subsong count $subsongCount")
        }
        val sampleHeaderSize = reader.u32le(OFFSET_SAMPLE_HEADER_SIZE) ?: throw Fsb5CorruptException("truncated header")
        val nameTableSize = reader.u32le(OFFSET_NAME_TABLE_SIZE) ?: throw Fsb5CorruptException("truncated header")
        val sampleDataSize = reader.u32le(OFFSET_SAMPLE_DATA_SIZE) ?: throw Fsb5CorruptException("truncated header")
        val codec = reader.u32le(OFFSET_CODEC) ?: throw Fsb5CorruptException("truncated header")
        val flags = if (version == VERSION_1) {
            reader.u32le(OFFSET_FLAGS) ?: throw Fsb5CorruptException("truncated version 1 header")
        } else {
            0L
        }
        if (flags != 0L) hatchet.v("FSB5: bank flags 0x${flags.toString(16)} for $identifier")

        val header = Fsb5Header(
            subsongCount = subsongCount.toInt(),
            sampleHeaderSize = sampleHeaderSize,
            nameTableSize = nameTableSize,
            sampleDataSize = sampleDataSize,
            codec = codec.toInt(),
            baseHeaderSize = baseHeaderSize,
            fileSize = bytes.size.toLong(),
        )
        // Upstream rejects a bank whose declared sections do not add up to the file length ("re-rip").
        if (header.totalSize != header.fileSize) {
            throw Fsb5CorruptException(
                "declared size ${header.totalSize} != file size ${header.fileSize}",
            )
        }

        if (!StreamedCodecs.isCodecAllowed(StreamedCodecs.Format.FSB5, header.codec)) {
            hatchet.d(
                "FSB5: codec 0x${header.codec.toString(16)} is not decodable by this libvgmstream " +
                    "build; no tracks for $identifier.",
            )
            return emptyList()
        }

        val subsongs = readSampleHeaders(reader, header)
        validateSampleData(header, subsongs)
        val names = readNames(reader, header)

        return subsongs.mapIndexed { index, subsong ->
            RawTrack(
                path = identifier,
                source = "",
                title = names[index],
                artist = TAG_UNKNOWN,
                game = TAG_UNKNOWN,
                length = subsong.lengthMs(),
                trackNumber = subsongTrackNumber(index),
                fadeLengthMs = 0L,
                platform = Platform.OTHER,
            )
        }
    }

    /** Walk the contiguous, variable-length sample headers that fill the sample-header section. */
    private fun readSampleHeaders(reader: ByteReader, header: Fsb5Header): List<Fsb5Subsong> {
        val subsongs = mutableListOf<Fsb5Subsong>()
        var offset = header.baseHeaderSize

        for (index in 0 until header.subsongCount) {
            if (offset + SAMPLE_MODE_SIZE > header.sampleHeaderEnd) {
                throw Fsb5CorruptException("sample header $index overruns the sample table")
            }
            val sampleMode = reader.u64le(offset) ?: throw Fsb5CorruptException("sample header $index is truncated")
            val numSamples = (sampleMode ushr SAMPLE_COUNT_SHIFT) and SAMPLE_COUNT_MASK
            val dataOffset = ((sampleMode ushr DATA_OFFSET_SHIFT) and DATA_OFFSET_MASK) shl DATA_OFFSET_SCALE
            val rateCode = ((sampleMode ushr RATE_CODE_SHIFT) and RATE_CODE_MASK).toInt()
            val sampleRate = SAMPLE_RATES.getOrNull(rateCode)
                ?: throw Fsb5CorruptException("sample header $index has invalid rate code $rateCode")

            var headerSize = SAMPLE_MODE_SIZE
            var loopStart = 0L
            var loopEnd = 0L
            var looped = false
            if (sampleMode and EXTRA_FLAGS_BIT != 0L) {
                val extra = readExtraFlags(reader, header, offset + SAMPLE_MODE_SIZE, numSamples)
                headerSize += extra.size
                loopStart = extra.loopStart
                loopEnd = extra.loopEnd
                looped = extra.looped
            }

            subsongs += Fsb5Subsong(numSamples, sampleRate, dataOffset, loopStart, loopEnd, looped)
            offset += headerSize
        }

        return subsongs
    }

    /**
     * Follow one subsong's extra-flag chain. Only loop info (type [EXTRA_TYPE_LOOP]) is metadata;
     * every other flag (channels, rate override, DSP coefficients, XMA/ATRAC9/Vorbis config, …) is
     * decoding-only and is skipped by its declared size.
     */
    private fun readExtraFlags(
        reader: ByteReader,
        header: Fsb5Header,
        firstOffset: Long,
        numSamples: Long,
    ): ExtraFlags {
        var size = 0L
        var loopStart = 0L
        var loopEnd = 0L
        var looped = false
        var offset = firstOffset

        while (true) {
            if (offset + EXTRA_FLAG_HEADER_SIZE > header.sampleHeaderEnd) {
                throw Fsb5CorruptException("extra flags overrun the sample table")
            }
            val flag = reader.u32le(offset) ?: throw Fsb5CorruptException("extra flag is truncated")
            val type = ((flag ushr EXTRA_TYPE_SHIFT) and EXTRA_TYPE_MASK).toInt()
            val flagSize = ((flag ushr EXTRA_SIZE_SHIFT) and EXTRA_SIZE_MASK).toInt()
            if (offset + EXTRA_FLAG_HEADER_SIZE + flagSize > header.sampleHeaderEnd) {
                throw Fsb5CorruptException("extra flag 0x${type.toString(16)} overruns the sample table")
            }

            if (type == EXTRA_TYPE_LOOP) {
                loopStart = reader.s32le(offset + 4) ?: throw Fsb5CorruptException("loop start is truncated")
                looped = true
                if (flagSize > 4) {
                    // Stored end is one lower than FMOD's own loop end (upstream adds 1).
                    loopEnd = (reader.s32le(offset + 8) ?: throw Fsb5CorruptException("loop end is truncated")) + 1
                }
                if (isUnwantedLoop(loopStart, loopEnd, numSamples)) looped = false
            }

            size += EXTRA_FLAG_HEADER_SIZE + flagSize
            offset += EXTRA_FLAG_HEADER_SIZE + flagSize
            if (flag and EXTRA_TERMINATOR_BIT != 0L) return ExtraFlags(size, loopStart, loopEnd, looped)
        }
    }

    /**
     * Upstream disables loops a game engine set for no reason: a whole-file loop (`loop_start == 0`
     * and the end within 1 152 samples of the last sample — the `is_small` guard is a no-op upstream
     * today) and one known bad-value pattern. Reproduce both so lengths match the native probe.
     */
    private fun isUnwantedLoop(loopStart: Long, loopEnd: Long, numSamples: Long): Boolean {
        val fullLoop = loopStart == 0L && loopEnd + FULL_LOOP_TOLERANCE >= numSamples
        val ajurikaLoop = loopStart == AJURIKA_LOOP_START &&
            loopEnd == AJURIKA_LOOP_END &&
            numSamples > loopEnd + AJURIKA_LOOP_GAP
        return fullLoop || ajurikaLoop
    }

    /**
     * Mirror upstream's data-offset checks: every subsong must have a non-empty slice of the data
     * section (its size is the next subsong's offset, or the section end for the last one) that lies
     * inside the file.
     */
    private fun validateSampleData(header: Fsb5Header, subsongs: List<Fsb5Subsong>) {
        for ((index, subsong) in subsongs.withIndex()) {
            val endOffset = if (index + 1 < subsongs.size) subsongs[index + 1].dataOffset else header.sampleDataSize
            val size = endOffset - subsong.dataOffset
            if (subsong.dataOffset < 0L || size <= 0L) {
                throw Fsb5CorruptException("subsong ${index + 1} has invalid sample-data offsets")
            }
            if (header.dataStart + endOffset > header.fileSize) {
                throw Fsb5CorruptException("subsong ${index + 1} points past the end of the file")
            }
        }
    }

    /**
     * One title per subsong from the name table. A zero offset means "no name", and a name made of
     * bytes upstream's `read_string` rejects (control bytes, or above 0xF0) reads as blank; both fall
     * back to [TAG_UNKNOWN] so the scanner's filename/`Track N` naming applies.
     */
    private fun readNames(reader: ByteReader, header: Fsb5Header): List<String> {
        if (header.nameTableSize == 0L) return List(header.subsongCount) { TAG_UNKNOWN }
        val entriesEnd = header.nameTableStart + ENTRY_SIZE * header.subsongCount
        if (entriesEnd > header.nameTableEnd) {
            throw Fsb5CorruptException("name table is too small for ${header.subsongCount} entries")
        }

        return List(header.subsongCount) { index ->
            val entryOffset = reader.u32le(header.nameTableStart + ENTRY_SIZE * index)
                ?: throw Fsb5CorruptException("name entry $index is truncated")
            if (entryOffset == 0L) TAG_UNKNOWN else readName(reader, header, entryOffset, index)
        }
    }

    private fun readName(reader: ByteReader, header: Fsb5Header, entryOffset: Long, index: Int): String {
        val start = header.nameTableStart + entryOffset
        if (start >= header.nameTableEnd) {
            throw Fsb5CorruptException("name entry $index points outside the name table")
        }

        val name = ByteArray(MAX_NAME_BYTES)
        var length = 0
        var position = start
        while (position < header.nameTableEnd) {
            if (length == MAX_NAME_BYTES) return decodeName(name, length)
            val byte = reader.u8(position) ?: throw Fsb5CorruptException("name entry $index is truncated")
            if (byte == 0) return decodeName(name, length)
            // Upstream's read_string gives up on control bytes and anything above 0xF0 (a broken
            // string reads as empty rather than leaking garbage into a title).
            if (byte < MIN_NAME_BYTE || byte > MAX_NAME_BYTE) return TAG_UNKNOWN
            name[length++] = byte.toByte()
            position++
        }
        throw Fsb5CorruptException("name entry $index is not NUL-terminated")
    }

    private fun decodeName(bytes: ByteArray, length: Int): String =
        bytes.copyOf(length).decodeToString().trim().ifBlank { TAG_UNKNOWN }
}

/** Structurally corrupt bank: aborts the parse and is reported as a miss by [readTracksFromFile]. */
private class Fsb5CorruptException(message: String) : Exception(message)

private data class Fsb5Header(
    val subsongCount: Int,
    val sampleHeaderSize: Long,
    val nameTableSize: Long,
    val sampleDataSize: Long,
    val codec: Int,
    val baseHeaderSize: Long,
    val fileSize: Long,
) {
    val sampleHeaderEnd: Long = baseHeaderSize + sampleHeaderSize
    val nameTableStart: Long = sampleHeaderEnd
    val nameTableEnd: Long = nameTableStart + nameTableSize
    val dataStart: Long = nameTableEnd
    val totalSize: Long = dataStart + sampleDataSize
}

private data class Fsb5Subsong(
    val numSamples: Long,
    val sampleRate: Int,
    val dataOffset: Long,
    val loopStart: Long,
    val loopEnd: Long,
    val looped: Boolean,
) {
    /**
     * `play_samples` under the probe/playback config `loop_count = 2.0`, `fade_time = 10.0`, then
     * the probe's truncating milliseconds conversion (`play_samples * 1000 / sample_rate`).
     */
    fun lengthMs(): Long {
        val samples = if (looped) {
            loopStart + (loopEnd - loopStart) * LOOP_PASSES + FADE_SECONDS * sampleRate
        } else {
            numSamples
        }
        return samples.coerceAtLeast(0L) * 1000L / sampleRate
    }
}

private data class ExtraFlags(
    val size: Long,
    val loopStart: Long,
    val loopEnd: Long,
    val looped: Boolean,
)

private fun ByteReader.u8(offset: Long): Int? {
    if (offset < 0L || offset + 1 > size.toLong()) return null
    return get(offset.toInt()).toInt() and 0xFF
}

private fun ByteReader.u32le(offset: Long): Long? {
    if (offset < 0L || offset + UINT32_SIZE > size.toLong()) return null
    return readIntLeAt(offset.toInt()).toLong() and UINT32_MASK
}

private fun ByteReader.s32le(offset: Long): Long? {
    if (offset < 0L || offset + UINT32_SIZE > size.toLong()) return null
    return readIntLeAt(offset.toInt()).toLong()
}

private fun ByteReader.u64le(offset: Long): Long? {
    if (offset < 0L || offset + UINT64_SIZE > size.toLong()) return null
    return readLongLeAt(offset.toInt())
}

private const val MAGIC = "FSB5"
private const val OFFSET_VERSION = 0x04L
private const val OFFSET_SUBSONGS = 0x08L
private const val OFFSET_SAMPLE_HEADER_SIZE = 0x0CL
private const val OFFSET_NAME_TABLE_SIZE = 0x10L
private const val OFFSET_SAMPLE_DATA_SIZE = 0x14L
private const val OFFSET_CODEC = 0x18L
private const val OFFSET_FLAGS = 0x20L
private const val VERSION_0 = 0L
private const val VERSION_1 = 1L
private const val BASE_HEADER_SIZE_V0 = 0x40L
private const val BASE_HEADER_SIZE_V1 = 0x3CL

/** The native probe refuses to enumerate more than this many subsongs of one bank. */
private const val MAX_SUBSONGS = 4096
private const val UINT32_SIZE = 4L
private const val UINT64_SIZE = 8L
private const val ENTRY_SIZE = 4L

private const val SAMPLE_MODE_SIZE = 8L
private const val SAMPLE_COUNT_SHIFT = 34
private const val SAMPLE_COUNT_MASK = 0x3FFF_FFFFL
private const val DATA_OFFSET_SHIFT = 7
private const val DATA_OFFSET_MASK = 0x07FF_FFFFL
private const val DATA_OFFSET_SCALE = 5
private const val RATE_CODE_SHIFT = 1
private const val RATE_CODE_MASK = 0x0FL
private const val EXTRA_FLAGS_BIT = 0x01L

private const val EXTRA_FLAG_HEADER_SIZE = 4L
private const val EXTRA_TYPE_SHIFT = 25
private const val EXTRA_TYPE_MASK = 0x7FL
private const val EXTRA_SIZE_SHIFT = 1
private const val EXTRA_SIZE_MASK = 0xFF_FFFFL
private const val EXTRA_TERMINATOR_BIT = 0x01L
private const val EXTRA_TYPE_LOOP = 0x03

private const val FULL_LOOP_TOLERANCE = 1152L
private const val AJURIKA_LOOP_START = 0x3CL
private const val AJURIKA_LOOP_END = 0x0080_0080L
private const val AJURIKA_LOOP_GAP = 10_000L

private const val MAX_NAME_BYTES = 255
private const val MIN_NAME_BYTE = 0x20
private const val MAX_NAME_BYTE = 0xF0

private const val LOOP_PASSES = 2L
private const val FADE_SECONDS = 10L

/** FMOD sample-rate codes, `meta/fsb5.c:124-139`; 11..15 are rejected by FMOD. */
private val SAMPLE_RATES = intArrayOf(
    4000,
    8000,
    11000,
    11025,
    16000,
    22050,
    24000,
    32000,
    44100,
    48000,
    96000,
)
