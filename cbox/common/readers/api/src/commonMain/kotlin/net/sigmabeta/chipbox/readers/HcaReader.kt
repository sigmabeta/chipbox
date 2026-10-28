package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * CRI HCA (`.hca`) — High Compression Audio from CRI ADX2/Atom middleware.
 *
 * Ports only the header parse from `cbox/native/vgmstream/Core/vgmstream/src/meta/hca.c` and its
 * bundled decoder's header reader (`src/coding/libs/clhca.c`, `clHCA_DecodeHeader`): the `HCA\0`
 * base header, the required `fmt` and `comp`/`dec` chunks, and the optional `loop`/`ciph` chunks.
 * Sample count, sample rate, channels, loop points and the frame (block) size come from there;
 * length then follows vgmstream's loop policy (2 loops + 10 s fade when looped) with the probe's
 * truncating millisecond conversion.
 *
 * **No key material is ever read.** HCA encryption (`ciph` type 56) protects the frame payload, not
 * the header, so metadata stays readable for encrypted files; the header magic comparison masks the
 * high bit exactly as upstream does for CRI's obfuscated headers. Only the metadata layer is ported
 * — playback still decodes through `libvgmstream`.
 */
class HcaReader(private val hatchet: Hatchet) : Reader() {
    override val version: Int = HCA_VERSION

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        val header = readHcaHeader(bytes, 0)
        if (header == null) {
            hatchet.w("HCA parse failed: $identifier has no supported HCA header.")
            return null
        }
        return listOf(header.toTrack(identifier))
    }

    companion object {
        const val HCA_VERSION = 1
    }
}

/**
 * One CRI HCA header, parsed from [readHcaHeader].
 *
 * Frame and block are the same unit here (`clHCA_stInfo.blockSize` / `blockCount`); each block holds
 * [SAMPLES_PER_BLOCK] samples.
 */
internal data class HcaHeader(
    val version: Int,
    val headerSize: Int,
    val channels: Int,
    val sampleRate: Int,
    val blockCount: Long,
    val blockSize: Int,
    val encoderDelay: Int,
    val encoderPadding: Int,
    val encrypted: Boolean,
    val loop: HcaLoop?,
) {
    /** Samples the stream decodes to, before the loop policy: `blocks * 1024 - delay - padding`. */
    val sampleCount: Long
        get() = blockCount * SAMPLES_PER_BLOCK - encoderDelay - encoderPadding

    /**
     * Length under the native probe's shared policy ([streamedLengthMs]): the looped play count
     * (intro + two loop passes + a ten-second fade) or the plain sample count, in truncating
     * milliseconds.
     */
    val lengthMs: Long
        get() = streamedLengthMs(
            numSamples = sampleCount,
            loopFlag = loop != null,
            loopStartSample = loop?.startSample ?: 0L,
            loopEndSample = loop?.endSample ?: 0L,
            sampleRate = sampleRate,
        )
}

/** Loop points in samples, as `clHCA_getInfo` derives them from the header's loop block. */
internal data class HcaLoop(val startSample: Long, val endSample: Long)

/**
 * Parses one HCA header starting at [offset] — a whole `.hca` file (offset 0) or an HCA cue
 * embedded in an ACB/AWB container (ticket 09).
 *
 * Returns null when the bytes at [offset] are not a structurally valid HCA header, and never throws.
 * Deliberately free of platform and charset APIs so it compiles on every `commonMain` target.
 */
internal fun readHcaHeader(bytes: ByteArray, offset: Int): HcaHeader? =
    try {
        parseHcaHeader(bytes, offset)
    } catch (e: HcaCorruptException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        // Defensive: a read that slips past its bounds check is still a miss, never a scan failure.
        null
    }

private fun parseHcaHeader(bytes: ByteArray, offset: Int): HcaHeader {
    if (offset < 0 || bytes.size - offset < BASE_HEADER_SIZE) {
        throw HcaCorruptException("truncated header")
    }
    val base = ByteReader.wrap(bytes, offset)
    if (!base.hasMaskedAsciiAt(0, SIGNATURE)) throw HcaCorruptException("missing HCA signature")
    val version = base.readShortBeAt(OFFSET_VERSION).toInt() and 0xFFFF
    val headerSize = base.readShortBeAt(OFFSET_HEADER_SIZE).toInt() and 0xFFFF
    if (version !in SUPPORTED_VERSIONS) {
        throw HcaCorruptException("unsupported version 0x${version.toString(16)}")
    }
    if (headerSize !in 1..MAX_HEADER_SIZE || headerSize > bytes.size - offset) {
        throw HcaCorruptException("invalid header size $headerSize")
    }
    if (hcaCrc16(bytes, offset, headerSize) != 0) throw HcaCorruptException("bad header checksum")

    return parseChunks(ByteReader.wrap(bytes, offset, headerSize), version, headerSize)
}

/**
 * Header CRC-16 as `clhca.c` computes it (`crc16_checksum` / `hcacommon_crc_mask_table`): MSB-first,
 * polynomial 0x8005, initial value 0, no reflection and no final xor. A complete HCA header —
 * chunks plus the trailing two checksum bytes — must checksum to 0.
 */
internal fun hcaCrc16(bytes: ByteArray, offset: Int, length: Int): Int {
    if (offset < 0 || length < 0 || offset + length > bytes.size) return -1
    var crc = 0
    for (i in offset until offset + length) {
        crc = crc xor ((bytes[i].toInt() and 0xFF) shl 8)
        repeat(8) {
            crc = if (crc and CRC_TOP_BIT != 0) {
                ((crc shl 1) xor CRC_POLYNOMIAL) and CRC_MASK
            } else {
                (crc shl 1) and CRC_MASK
            }
        }
    }
    return crc
}

/**
 * Walk the header's chunk list in the fixed order `clHCA_DecodeHeader` requires: `fmt`, then
 * `comp` (v2.0+) or `dec` (v1.x), then the optional blocks. Only chunks that carry metadata are
 * read; `vbr`, `ath`, `rva`, `comm` and `pad` are decoding-only.
 */
private fun parseChunks(header: ByteReader, version: Int, headerSize: Int): HcaHeader {
    val format = readFormatChunk(header, OFFSET_FIRST_CHUNK)
    val compression = readCompressionChunk(header, format.endOffset, version)

    // Optional decode-only chunks before `loop`: `vbr` (dropped in v2.0) and `ath` (v1.x only).
    var offset = compression.endOffset
    if (header.hasMaskedAsciiAt(offset, VBR_MAGIC)) offset += VBR_CHUNK_SIZE
    if (header.hasMaskedAsciiAt(offset, ATH_MAGIC)) offset += ATH_CHUNK_SIZE

    var loop: HcaLoop? = null
    if (header.hasMaskedAsciiAt(offset, LOOP_MAGIC)) {
        loop = readLoopChunk(header, offset, format.blockCount, format.encoderDelay)
        offset += LOOP_CHUNK_SIZE
    }

    val encrypted = readCipherChunk(header, offset)

    return HcaHeader(
        version = version,
        headerSize = headerSize,
        channels = format.channels,
        sampleRate = format.sampleRate,
        blockCount = format.blockCount,
        blockSize = compression.blockSize,
        encoderDelay = format.encoderDelay,
        encoderPadding = format.encoderPadding,
        encrypted = encrypted,
        loop = loop,
    )
}

private data class HcaFormat(
    val channels: Int,
    val sampleRate: Int,
    val blockCount: Long,
    val encoderDelay: Int,
    val encoderPadding: Int,
    val endOffset: Int,
)

/** The `fmt` chunk: channels, 24-bit sample rate, block count and the encoder delay/padding. */
private fun readFormatChunk(header: ByteReader, offset: Int): HcaFormat {
    if (!header.hasMaskedAsciiAt(offset, FMT_MAGIC)) throw HcaCorruptException("missing fmt chunk")
    val channels = header.u8At(offset + 0x04) ?: throw HcaCorruptException("truncated fmt chunk")
    val sampleRate = header.u24beAt(offset + 0x05) ?: throw HcaCorruptException("truncated fmt chunk")
    val blockCount = header.u32beAt(offset + 0x08) ?: throw HcaCorruptException("truncated fmt chunk")
    val encoderDelay = header.u16beAt(offset + 0x0C) ?: throw HcaCorruptException("truncated fmt chunk")
    val encoderPadding = header.u16beAt(offset + 0x0E) ?: throw HcaCorruptException("truncated fmt chunk")

    if (channels !in MIN_CHANNELS..MAX_CHANNELS) {
        throw HcaCorruptException("invalid channel count $channels")
    }
    if (blockCount == 0L) throw HcaCorruptException("empty stream")
    if (sampleRate !in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) {
        throw HcaCorruptException("invalid sample rate $sampleRate")
    }
    return HcaFormat(channels, sampleRate, blockCount, encoderDelay, encoderPadding, offset + FMT_CHUNK_SIZE)
}

private data class HcaCompression(val blockSize: Int, val endOffset: Int)

/** The `loop` chunk. Upstream models the loop in samples, bounded by the stream's block count. */
private fun readLoopChunk(
    header: ByteReader,
    offset: Int,
    blockCount: Long,
    encoderDelay: Int,
): HcaLoop {
    val startFrame = header.u32beAt(offset + 0x04) ?: throw HcaCorruptException("truncated loop chunk")
    val endFrame = header.u32beAt(offset + 0x08) ?: throw HcaCorruptException("truncated loop chunk")
    val startDelay = header.u16beAt(offset + 0x0C) ?: throw HcaCorruptException("truncated loop chunk")
    val endPadding = header.u16beAt(offset + 0x0E) ?: throw HcaCorruptException("truncated loop chunk")

    // `clHCA_DecodeHeader` requires loop start <= loop end < block count.
    if (startFrame > endFrame || endFrame >= blockCount) {
        throw HcaCorruptException("invalid loop $startFrame..$endFrame of $blockCount blocks")
    }
    return HcaLoop(
        startSample = startFrame * SAMPLES_PER_BLOCK - encoderDelay + startDelay,
        endSample = endFrame * SAMPLES_PER_BLOCK - encoderDelay + (SAMPLES_PER_BLOCK - endPadding),
    )
}

/** The `comp` (v2.0+) or `dec` (v1.x) chunk; both start with the frame/block size. */
private fun readCompressionChunk(header: ByteReader, offset: Int, version: Int): HcaCompression {
    val chunkSize = when {
        header.hasMaskedAsciiAt(offset, COMP_MAGIC) -> COMP_CHUNK_SIZE
        header.hasMaskedAsciiAt(offset, DEC_MAGIC) -> DEC_CHUNK_SIZE
        else -> throw HcaCorruptException("missing comp/dec chunk")
    }
    val blockSize = header.u16beAt(offset + 0x04) ?: throw HcaCorruptException("truncated comp/dec chunk")
    if (blockSize !in MIN_BLOCK_SIZE..MAX_BLOCK_SIZE) {
        throw HcaCorruptException("invalid block size $blockSize")
    }

    val minResolution = header.u8At(offset + 0x06) ?: throw HcaCorruptException("truncated comp/dec chunk")
    val maxResolution = header.u8At(offset + 0x07) ?: throw HcaCorruptException("truncated comp/dec chunk")
    if (maxResolution > MAX_RESOLUTION) throw HcaCorruptException("invalid max resolution $maxResolution")
    // Up to v2.0 the range is fixed; v3.0 allows any ordered range up to 15.
    val resolutionsValid = if (version <= VERSION_200) {
        minResolution == MIN_RESOLUTION && maxResolution == MAX_RESOLUTION
    } else {
        minResolution <= maxResolution
    }
    if (!resolutionsValid) {
        throw HcaCorruptException("invalid resolutions $minResolution..$maxResolution")
    }

    validateBands(header, offset, chunkSize == DEC_CHUNK_SIZE)
    return HcaCompression(blockSize, offset + chunkSize)
}

/**
 * Upstream caps every band count at 128 (spectrum points per subframe). `dec` stores total/base one
 * lower and derives the stereo bands from `stereo_type`; `comp` stores all three directly.
 */
private fun validateBands(header: ByteReader, offset: Int, dec: Boolean) {
    val total: Int
    val base: Int
    val stereo: Int
    val bandsPerHfr: Int
    if (dec) {
        total = (header.u8At(offset + 0x08) ?: throw HcaCorruptException("truncated dec chunk")) + 1
        val storedBase = (header.u8At(offset + 0x09) ?: throw HcaCorruptException("truncated dec chunk")) + 1
        val stereoType = header.u8At(offset + 0x0B) ?: throw HcaCorruptException("truncated dec chunk")
        base = if (stereoType == 0) total else storedBase
        stereo = total - base
        bandsPerHfr = 0
    } else {
        total = header.u8At(offset + 0x0A) ?: throw HcaCorruptException("truncated comp chunk")
        base = header.u8At(offset + 0x0B) ?: throw HcaCorruptException("truncated comp chunk")
        stereo = header.u8At(offset + 0x0C) ?: throw HcaCorruptException("truncated comp chunk")
        bandsPerHfr = header.u8At(offset + 0x0D) ?: throw HcaCorruptException("truncated comp chunk")
    }
    if (total > MAX_BANDS || base > MAX_BANDS || stereo > MAX_BANDS ||
        base + stereo > MAX_BANDS || bandsPerHfr > MAX_BANDS
    ) {
        throw HcaCorruptException("invalid band layout $total/$base/$stereo/$bandsPerHfr")
    }
}

/** The `ciph` chunk. Type 56 is keycode encryption of the payload; metadata needs no key. */
private fun readCipherChunk(header: ByteReader, offset: Int): Boolean {
    if (!header.hasMaskedAsciiAt(offset, CIPH_MAGIC)) return false
    val type = header.u16beAt(offset + 0x04) ?: throw HcaCorruptException("truncated ciph chunk")
    if (type !in CIPH_TYPES) throw HcaCorruptException("unsupported cipher type $type")
    return type == CIPH_KEYCODE
}

// HCA has no stream name: the native probe reports a blank `stream_name` (the optional `comm`
// chunk is ignored upstream), so the default TAG_UNKNOWN title lets the scanner fall back to the
// filename.
private fun HcaHeader.toTrack(identifier: String) = streamedRawTrack(identifier, lengthMs)

/** Structurally invalid header: aborts the parse, which [readHcaHeader] reports as null. */
private class HcaCorruptException(message: String) : Exception(message)

/** CRI masks the high bit of each header magic byte when the file is encrypted (`HCA_MASK`). */
private fun ByteReader.hasMaskedAsciiAt(offset: Int, magic: String): Boolean {
    if (offset < 0 || offset + magic.length > size) return false
    return magic.indices.all { (get(offset + it).toInt() and MAGIC_MASK) == magic[it].code }
}

private fun ByteReader.u8At(index: Int): Int? =
    if (index < 0 || index + 1 > size) null else get(index).toInt() and 0xFF

private fun ByteReader.u16beAt(index: Int): Int? =
    if (index < 0 || index + 2 > size) null else readShortBeAt(index).toInt() and 0xFFFF

private fun ByteReader.u24beAt(index: Int): Int? {
    if (index < 0 || index + 3 > size) return null
    return ((get(index).toInt() and 0xFF) shl 16) or
        ((get(index + 1).toInt() and 0xFF) shl 8) or
        (get(index + 2).toInt() and 0xFF)
}

private fun ByteReader.u32beAt(index: Int): Long? =
    if (index < 0 || index + 4 > size) null else readIntBeAt(index).toLong() and UINT32_MASK

internal const val EXT_HCA = "hca"

private const val SIGNATURE = "HCA\u0000"
private const val MAGIC_MASK = 0x7F
private const val BASE_HEADER_SIZE = 0x08
private const val OFFSET_VERSION = 0x04
private const val OFFSET_HEADER_SIZE = 0x06
private const val OFFSET_FIRST_CHUNK = 0x08
private const val FMT_MAGIC = "fmt\u0000"
private const val FMT_CHUNK_SIZE = 0x10
private const val COMP_MAGIC = "comp"
private const val COMP_CHUNK_SIZE = 0x10
private const val DEC_MAGIC = "dec\u0000"
private const val DEC_CHUNK_SIZE = 0x0C
private const val VBR_MAGIC = "vbr\u0000"
private const val VBR_CHUNK_SIZE = 0x08
private const val ATH_MAGIC = "ath\u0000"
private const val ATH_CHUNK_SIZE = 0x06
private const val LOOP_MAGIC = "loop"
private const val LOOP_CHUNK_SIZE = 0x10
private const val CIPH_MAGIC = "ciph"
private const val CIPH_KEYCODE = 56
private val CIPH_TYPES = setOf(0, 1, CIPH_KEYCODE)
private const val MAX_HEADER_SIZE = 0x1000
private const val CRC_POLYNOMIAL = 0x8005
private const val CRC_TOP_BIT = 0x8000
private const val CRC_MASK = 0xFFFF
private const val MIN_CHANNELS = 1
private const val MAX_CHANNELS = 16
private const val MIN_SAMPLE_RATE = 1
private const val MAX_SAMPLE_RATE = 0x7F_FFFF
private const val MIN_BLOCK_SIZE = 0x08
private const val MAX_BLOCK_SIZE = 0xFFFF

/** HCA header versions `clHCA_DecodeHeader` accepts (v1.1–v1.4, v2.0, v3.0). */
private val SUPPORTED_VERSIONS = setOf(0x0101, 0x0102, 0x0103, 0x0200, 0x0300)

private const val VERSION_200 = 0x0200
private const val MIN_RESOLUTION = 1
private const val MAX_RESOLUTION = 15
private const val MAX_BANDS = 128

/** Samples in one HCA block: 8 subframes of 128 samples. */
private const val SAMPLES_PER_BLOCK = 1024L
