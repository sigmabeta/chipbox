package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * CRI AIX (`.aix`) — a segmented container of CRI ADX streams, optionally layered for multichannel.
 *
 * **AIX is not an HCA cue sheet.** Upstream's `meta/aix.c` (`init_vgmstream_aix`) parses a segment
 * table and a layer table, builds one layered ADX stream per segment, and joins those segments into
 * **one** vgmstream with `layout_segmented`. The segment count therefore drives the loop policy, not
 * subsong count: a file always yields a single `RawTrack`, and its length is the sum of the segment
 * sample counts (from this header table — the per-layer ADX headers are never consulted for it).
 *
 * The loop is derived purely from segment boundaries, exactly as
 * `util/layout_utils.c:allocate_segmented_vgmstream` does with `layout/segmented.c`'s arguments:
 * `loop_start_segment = 2` / `loop_end_segment = count - 2` for more than three segments, else
 * segment 1 for both; looping only for 1–5 segments, and never for upstream's truncated-three-segment
 * special case. `base/play_state.c` then plays that loop twice plus a 10-second fade, which is what
 * [streamedLengthMs] reproduces.
 *
 * Only the metadata layer is ported — playback still decodes through `libvgmstream`. AIX carries no
 * stream or cue names, so the track keeps the [TAG_UNKNOWN] title and the scanner's filename
 * fallback applies. A malformed header is a parse miss (`null`, logged at `w`), never a throw: like
 * upstream, one invalid segment, layer or offset invalidates the whole file, because the segments
 * are one continuous stream rather than independent subsongs.
 *
 * One deliberate narrowing: the segment payloads are `AIXP` blocks that upstream deblocks into ADX
 * streams, and a payload whose ADX header fails to parse fails the whole file there. This reader
 * validates the AIX tables and the byte ranges they point at but does not descend into the payload,
 * because nothing in it contributes metadata — length and loop come entirely from the segment table.
 */
class AixReader(private val hatchet: Hatchet) : Reader() {
    override val version: Int = AIX_VERSION

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        val header = readAixHeader(bytes)
        if (header == null) {
            hatchet.w("AIX parse failed: $identifier has no supported AIX header.")
            return null
        }
        return listOf(header.toTrack(identifier))
    }

    companion object {
        const val AIX_VERSION = 1
    }
}

/**
 * One parsed AIX header: the segments upstream would play, the sample rate they share, and the loop
 * `allocate_segmented_vgmstream` derives from their boundaries.
 */
internal data class AixHeader(
    val segmentCount: Int,
    val sampleRate: Int,
    val numSamples: Long,
    val loops: Boolean,
    val loopStartSample: Long,
    val loopEndSample: Long,
) {
    /** Length under the native probe's shared policy ([streamedLengthMs]). */
    val lengthMs: Long
        get() = streamedLengthMs(
            numSamples = numSamples,
            loopFlag = loops,
            loopStartSample = loopStartSample,
            loopEndSample = loopEndSample,
            sampleRate = sampleRate,
        )

    fun toTrack(identifier: String): RawTrack = streamedRawTrack(identifier, lengthMs)
}

/** Parses one AIX header; `null` (never a throw) when the bytes are not a structurally valid AIX. */
internal fun readAixHeader(bytes: ByteArray): AixHeader? =
    try {
        parseAixHeader(bytes)
    } catch (e: AixCorruptException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        // Defensive: a read that slips past its bounds check is still a miss, never a scan failure.
        null
    }

private fun parseAixHeader(bytes: ByteArray): AixHeader {
    if (bytes.size < MIN_HEADER_SIZE) throw AixCorruptException("truncated header")
    val header = ByteReader.wrap(bytes)
    if (!header.hasAsciiPrefix(MAGIC)) throw AixCorruptException("missing AIXF signature")
    if (header.readIntBeAt(OFFSET_VERSION) != VERSION) throw AixCorruptException("unsupported version")
    if (header.readIntBeAt(OFFSET_HEADER_SIZE) != HEADER_SIZE) {
        throw AixCorruptException("unsupported header size")
    }

    val dataOffset = header.readIntBeAt(OFFSET_DATA) + DATA_OFFSET_BIAS
    val segmentCount = header.readShortBeAt(OFFSET_SEGMENT_COUNT).toInt() and 0xFFFF
    if (segmentCount !in 1..MAX_SEGMENTS) {
        throw AixCorruptException("invalid segment count $segmentCount")
    }
    // Upstream sizes the subtable from the *declared* count, before any truncation adjustment.
    val subtableOffset = SEGMENT_TABLE_OFFSET + segmentCount * SEGMENT_ENTRY_SIZE
    if (subtableOffset >= dataOffset) throw AixCorruptException("segment table overlaps the audio data")

    val segments = readSegmentTable(header, segmentCount, dataOffset, bytes.size)
    readLayerTable(header, subtableOffset, dataOffset, segments.rate)
    return segments.asHeader()
}

/** One AIX segment table entry: byte range in the file, sample count, and the table-wide sample rate. */
private class AixSegmentTable(
    val count: Int,
    val offsets: LongArray,
    val sizes: LongArray,
    val samples: LongArray,
    val rate: Int,
    var usedCount: Int = count,
    val forceDisableLoop: Boolean = false,
) {
    /**
     * Derives num_samples and the loop points from the segment boundaries, mirroring
     * `allocate_segmented_vgmstream` plus the `setup_vgmstream` sanitisation that clears a loop which
     * does not describe a real, non-empty section of the stream.
     */
    fun asHeader(): AixHeader {
        val looping = !forceDisableLoop && usedCount in 1..LOOP_MAX_SEGMENTS
        val loopStartSegment = if (usedCount > LOOP_PIVOT_SEGMENTS) 2 else 1
        val loopEndSegment = if (usedCount > LOOP_PIVOT_SEGMENTS) usedCount - 2 else 1

        var numSamples = 0L
        var loopStart = 0L
        var loopEnd = 0L
        for (i in 0 until usedCount) {
            if (looping && i == loopStartSegment) loopStart = numSamples
            numSamples += samples[i]
            if (looping && i == loopEndSegment) loopEnd = numSamples
        }

        val loops = looping && loopStart >= 0 && loopEnd > loopStart && loopEnd <= numSamples
        return AixHeader(
            segmentCount = usedCount,
            sampleRate = rate,
            numSamples = numSamples,
            loops = loops,
            loopStartSample = if (loops) loopStart else 0L,
            loopEndSample = if (loops) loopEnd else 0L,
        )
    }
}

/**
 * Reads the segment table and applies upstream's two source-file adjustments: segments after the
 * first may inherit the first segment's sample rate, and a three-segment file whose second segment
 * runs past EOF is treated as its intro/loop pair with looping force-disabled (the media is
 * truncated on disc; the missing tail plays as silence).
 */
private fun readSegmentTable(
    header: ByteReader,
    count: Int,
    dataOffset: Int,
    fileSize: Int,
): AixSegmentTable {
    val offsets = LongArray(count)
    val sizes = LongArray(count)
    val samples = LongArray(count)
    val rates = IntArray(count)
    for (i in 0 until count) {
        val entry = SEGMENT_TABLE_OFFSET + i * SEGMENT_ENTRY_SIZE
        offsets[i] = header.readIntBeAt(entry).toLong() and 0xFFFF_FFFFL
        sizes[i] = header.readIntBeAt(entry + SEGMENT_ENTRY_SIZE_OFFSET).toLong() and 0xFFFF_FFFFL
        samples[i] = header.readIntBeAt(entry + SEGMENT_ENTRY_SAMPLES_OFFSET).toLong()
        rates[i] = header.readIntBeAt(entry + SEGMENT_ENTRY_RATE_OFFSET)
        if (i > 0 && rates[i] == 0) rates[i] = rates[0]
        if (rates[i] != rates[0]) throw AixCorruptException("segment $i has a different sample rate")
    }
    if (offsets[0] != dataOffset.toLong()) {
        throw AixCorruptException("first segment does not start at the audio data offset")
    }

    val truncated = count == TRUNCATED_SEGMENT_COUNT && offsets[1] + sizes[1] > fileSize
    val table = if (truncated) {
        // Upstream drops the (missing) end segment and plays the intro/loop pair without looping.
        sizes[1] = fileSize - offsets[1]
        AixSegmentTable(count, offsets, sizes, samples, rates[0], usedCount = 2, forceDisableLoop = true)
    } else {
        AixSegmentTable(count, offsets, sizes, samples, rates[0])
    }
    validateSegmentData(table, fileSize)
    return table
}

/** Every played segment must lie inside the file; one bad range invalidates the whole container. */
private fun validateSegmentData(table: AixSegmentTable, fileSize: Int) {
    for (i in 0 until table.usedCount) {
        val offset = table.offsets[i]
        val size = table.sizes[i]
        if (size < 0 || offset > fileSize - size) {
            throw AixCorruptException("segment $i points outside the file")
        }
    }
}

/**
 * Validates the layer table the way upstream does: a marker byte, at least one layer, all layers
 * sharing the segments' sample rate, and the whole table fitting before the audio data.
 */
private fun readLayerTable(header: ByteReader, subtableOffset: Int, dataOffset: Int, rate: Int) {
    if (header.get(subtableOffset).toInt() and 0xFF != SUBTABLE_MARKER) {
        throw AixCorruptException("missing segment/layer subtable marker")
    }
    val layerListOffset = subtableOffset + SUBTABLE_SIZE
    if (layerListOffset >= dataOffset) throw AixCorruptException("layer table overlaps the audio data")
    val layerCount = header.get(layerListOffset).toInt() and 0xFF
    if (layerCount < 1) throw AixCorruptException("invalid layer count $layerCount")
    val layerListEnd = layerListOffset + LAYER_HEADER_SIZE + layerCount * LAYER_ENTRY_SIZE
    if (layerListEnd >= dataOffset) throw AixCorruptException("layer table overlaps the audio data")
    for (i in 0 until layerCount) {
        val entry = layerListOffset + LAYER_HEADER_SIZE + i * LAYER_ENTRY_SIZE
        if (header.readIntBeAt(entry) != rate) {
            throw AixCorruptException("layer $i has a different sample rate")
        }
    }
}

private class AixCorruptException(message: String) : Exception(message)

internal const val EXT_AIX = "aix"

private const val MAGIC = "AIXF"
private const val MIN_HEADER_SIZE = 0x20
private const val OFFSET_DATA = 0x04
private const val OFFSET_VERSION = 0x08
private const val OFFSET_HEADER_SIZE = 0x0C
private const val OFFSET_SEGMENT_COUNT = 0x18
private const val DATA_OFFSET_BIAS = 0x08
private const val VERSION = 0x01000014
private const val HEADER_SIZE = 0x00000800
private const val SEGMENT_TABLE_OFFSET = 0x20
private const val SEGMENT_ENTRY_SIZE = 0x10
private const val SEGMENT_ENTRY_SIZE_OFFSET = 0x04
private const val SEGMENT_ENTRY_SAMPLES_OFFSET = 0x08
private const val SEGMENT_ENTRY_RATE_OFFSET = 0x0C
private const val MAX_SEGMENTS = 120
private const val TRUNCATED_SEGMENT_COUNT = 3
private const val SUBTABLE_MARKER = 0x01
private const val SUBTABLE_SIZE = 0x10
private const val LAYER_HEADER_SIZE = 0x08
private const val LAYER_ENTRY_SIZE = 0x08
private const val LOOP_MAX_SEGMENTS = 5
private const val LOOP_PIVOT_SEGMENTS = 3
