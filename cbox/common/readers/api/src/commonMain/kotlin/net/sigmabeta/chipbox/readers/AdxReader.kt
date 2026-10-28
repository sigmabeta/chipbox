package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * CRI ADX (`.adx`) — a streamed ADPCM format used by Sega Dreamcast, NAOMI and many CRI tools.
 * Ports the header parse from vgmstream's `meta/adx.c`: sample rate, sample count and the optional
 * loop points, from which the play length follows vgmstream's loop policy (2 loops + 10 s fade).
 *
 * Only the metadata layer is ported. Encrypted variants (`0x0408`/`0x0409`) share the `0x0400`
 * layout and are read without a key — the key only XORs the payload, never the fields this reader
 * needs.
 */
class AdxReader(private val hatchet: Hatchet) : Reader() {
    override val version: Int = ADX_VERSION

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        val header = readAdxHeader(bytes, 0)
        if (header == null) {
            hatchet.w("ADX parse failed: $identifier is not a supported ADX header.")
            return null
        }
        return listOf(header.toTrack(identifier))
    }

    companion object {
        const val ADX_VERSION = 1
    }
}

/**
 * One parsed ADX header. Exposed to the AWB reader, which parses ADX cues embedded in a wave bank
 * (upstream's `meta/awb.c` sniffs `0x8000` and hands the subfile to the ADX parser).
 */
internal data class AdxHeader(
    val sampleRate: Int,
    val numSamples: Int,
    val loop: AdxLoop?,
    /** Byte offset of the audio payload (header size), for an embedded cue's bounds check. */
    val dataStart: Int,
) {
    /**
     * Length in the same terms as the native probe: the looped play count from
     * `base/play_state.c` (loop start + one extra loop iteration + the configured fade), or the
     * plain sample count when the file does not loop. Integer division truncates, like the probe's
     * `play_samples * 1000 / sample_rate`.
     */
    val lengthMs: Long
        get() {
            val playSamples = loop?.let {
                it.start + (it.end - it.start) * LOOP_COUNT + FADE_SECONDS * sampleRate
            } ?: numSamples.toLong()
            return playSamples * MILLIS_PER_SECOND / sampleRate
        }

    fun toTrack(identifier: String) = RawTrack(
        path = identifier,
        source = "",
        // ADX headers carry no stream name (only the rarely used CINF block has a filename), so the
        // scanner's filename fallback supplies the title.
        title = TAG_UNKNOWN,
        artist = TAG_UNKNOWN,
        game = TAG_UNKNOWN,
        length = lengthMs,
        trackNumber = subsongTrackNumber(0),
        fadeLengthMs = 0L,
        platform = platformForStreamedExtension(EXT_ADX),
    )
}

/** Loop points in samples, already read from whichever offset this header generation uses. */
internal data class AdxLoop(val start: Long, val end: Long)

/**
 * Parses one ADX header starting at [offset] — a whole `.adx` file (offset 0) or an ADX cue
 * embedded in an AWB wave bank (ticket 09, mirroring upstream's `load_meta_type`).
 *
 * Returns null when the bytes at [offset] are not a structurally valid ADX header, and never throws.
 */
internal fun readAdxHeader(bytes: ByteArray, offset: Int): AdxHeader? =
    try {
        if (offset < 0 || bytes.size - offset < MIN_HEADER_SIZE) {
            null
        } else {
            parseSupportedHeader(bytesAsReader(bytes, offset))
        }
    } catch (e: IndexOutOfBoundsException) {
        // Defensive: a read that slips past its bounds check is still a miss, never a scan failure.
        null
    }

/**
 * Validates the fields vgmstream requires before it will touch a file, then hands off to the
 * version-specific loop parse. Split from [readAdxHeader] so underflow from a truncated header is
 * caught in one place; a null here means "unsupported", never "mis-parsed".
 */
private fun parseSupportedHeader(reader: ByteReader): AdxHeader? {
    val encodingType = reader.u8At(0x04)
    val frameSize = reader.u8At(0x05)
    val bitsPerSample = reader.u8At(0x06)
    val channels = reader.u8At(0x07)
    val sampleRate = reader.readIntBeAt(0x08)
    val numSamples = reader.readIntBeAt(0x0C)
    val version = reader.u16beAt(0x12)
    val startOffset = reader.u16beAt(0x02) + HEADER_SIZE_BIAS

    val headerSupported = reader.u16beAt(0x00) == ADX_SIGNATURE &&
        encodingType in MIN_ENCODING..MAX_ENCODING &&
        frameSize == FRAME_SIZE && bitsPerSample == BITS_PER_SAMPLE
    // A negative sample count cannot describe a track: upstream would emit a negative length and
    // the scanner would rewrite it to its unknown-length default, so reject it here instead.
    val streamSupported = channels in 1..MAX_CHANNELS && sampleRate > 0 && numSamples >= 0
    val streamLocated = startOffset in CRI_MAGIC_SIZE..reader.size &&
        reader.hasAsciiAt(startOffset - CRI_MAGIC_SIZE, CRI_MAGIC)
    if (!headerSupported || !streamSupported || !streamLocated) return null

    return generationOf(version)?.let { generation ->
        AdxHeader(sampleRate, numSamples, readLoop(reader, generation, channels, startOffset), startOffset)
    }
}

/** Header generations vgmstream supports; `0x0408`/`0x0409` are encrypted `0x0400` layouts. */
private fun generationOf(version: Int): AdxGeneration? = when (version) {
    VERSION_0300 -> AdxGeneration.V03
    VERSION_0400, VERSION_0408, VERSION_0409 -> AdxGeneration.V04
    VERSION_0500 -> AdxGeneration.V05
    else -> null
}

private enum class AdxGeneration { V03, V04, V05 }

/**
 * Reads the loop block when the header is long enough to contain one, else null. v0x0300 fixes it
 * at 0x14; v0x0400 places it after the per-channel sample history, which shifts by 4 bytes per
 * channel (minimum 8), and its size is reduced by an `AINF` block when one is present. v0x0500 has
 * no loop info at all.
 */
private fun readLoop(
    reader: ByteReader,
    generation: AdxGeneration,
    channels: Int,
    startOffset: Int,
): AdxLoop? {
    val loopsOffset = loopInfoOffset(reader, generation, channels, startOffset) ?: return null
    val looping = reader.readIntBeAt(loopsOffset + LOOP_FLAG_OFFSET) != 0
    return if (looping) {
        AdxLoop(
            start = reader.readIntBeAt(loopsOffset + LOOP_START_OFFSET).toLong(),
            end = reader.readIntBeAt(loopsOffset + LOOP_END_OFFSET).toLong(),
        )
    } else {
        null
    }
}

private fun loopInfoOffset(
    reader: ByteReader,
    generation: AdxGeneration,
    channels: Int,
    startOffset: Int,
): Int? = when (generation) {
    AdxGeneration.V03 -> LEGACY_LOOPS_OFFSET.takeIf {
        startOffset - CRI_MAGIC_SIZE >= LEGACY_LOOPS_OFFSET + LOOP_INFO_SIZE
    }

    AdxGeneration.V04 -> {
        val loopsOffset = HISTORY_OFFSET + historySize(channels)
        val ainfSize = ainfSizeAfter(reader, loopsOffset + AINF_DISPLACEMENT)
        val headerEnd = startOffset.toLong() - ainfSize - CRI_MAGIC_SIZE
        loopsOffset.takeIf { headerEnd >= loopsOffset + LOOP_INFO_SIZE }
    }

    AdxGeneration.V05 -> null
}

/** Size of the `AINF` block at [offset] (its declared size field), or 0 when there is none. */
private fun ainfSizeAfter(reader: ByteReader, offset: Int): Long =
    if (reader.hasAsciiAt(offset, AINF_MAGIC)) {
        reader.readIntBeAt(offset + AINF_SIZE_OFFSET).toLong() and 0xFFFF_FFFFL
    } else {
        0L
    }

/** 4 bytes per channel of sample history, but never fewer than 8 (even in mono files). */
private fun historySize(channels: Int): Int = if (channels > 1) 4 * channels else 8

private fun ByteReader.u16beAt(index: Int): Int = readShortBeAt(index).toInt() and 0xFFFF

private fun ByteReader.u8At(index: Int): Int = get(index).toInt() and 0xFF

internal const val EXT_ADX = "adx"

private const val MIN_HEADER_SIZE = 0x14
private const val MILLIS_PER_SECOND = 1_000L
private const val LOOP_COUNT = 2L
private const val FADE_SECONDS = 10L
private const val ADX_SIGNATURE = 0x8000

/** `u16be(0x02)` is the header size excluding the first four bytes; `(c)CRI` sits 6 before data. */
private const val HEADER_SIZE_BIAS = 4
private const val CRI_MAGIC = "(c)CRI"
private const val CRI_MAGIC_SIZE = 6
private const val MIN_ENCODING = 0x02
private const val MAX_ENCODING = 0x04
private const val FRAME_SIZE = 0x12
private const val BITS_PER_SAMPLE = 4
private const val MAX_CHANNELS = 8
private const val HISTORY_OFFSET = 0x18
private const val LEGACY_LOOPS_OFFSET = 0x14
private const val LOOP_INFO_SIZE = 0x18
private const val LOOP_FLAG_OFFSET = 0x04
private const val LOOP_START_OFFSET = 0x08
private const val LOOP_END_OFFSET = 0x10
private const val AINF_MAGIC = "AINF"
private const val AINF_DISPLACEMENT = 4
private const val AINF_SIZE_OFFSET = 4
private const val VERSION_0300 = 0x0300
private const val VERSION_0400 = 0x0400
private const val VERSION_0408 = 0x0408
private const val VERSION_0409 = 0x0409
private const val VERSION_0500 = 0x0500
