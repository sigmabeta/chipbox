package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * EA SCHl — the block-chunked stream container behind EA Canada's `sx.exe`: early `.asf`/`.str`/
 * `.chk` streams, mid-2000s `.eam`/`.exa`/`.sng`/`.aud` music and 3DS `.stm`. Port of vgmstream's
 * `meta/ea_schl.c` + `meta/ea_schl_standard.c` (header parse) and `layout/blocked_ea_schl.c`
 * (block-chain walk); only the metadata layer is ported — playback still goes through libvgmstream.
 *
 * Layout: a `SCHl` (or localized `SHxx`) block carrying a patch-encoded header, then a chain of
 * `SCCl` (block count), `SCDl` (audio data) and `SCLl` (loop end) blocks closed by `SCEl`. The
 * audio fields live in the header patches: platform (`PT`), channels, sample rate, sample count
 * and the loop points, plus a platform-dependent default table for anything the file omits. A
 * localized multi-language file chains one header block per stream, so each yields a track; the
 * standard single-header file yields one.
 *
 * Length follows the native probe exactly: a looping stream plays
 * `loop_start + (loop_end - loop_start) * 2 + 10 * sample_rate` samples (upstream adds 1 to the
 * stored loop end), otherwise its sample count, and milliseconds are `samples * 1000 / rate` with
 * integer truncation — see [streamedLengthMs]. Streamed tracks keep `fadeLengthMs = 0`; the
 * scanner's zero-fade padding is unchanged.
 *
 * Codec gate: only the EA-XA, EA-ADPCM/MicroTalk and PCM codecs this build decodes are accepted
 * (see [StreamedCodecs.isCodecAllowed]). A file whose streams need MPEG (Layer II/III, EA
 * Layer 3) or FFmpeg (ATRAC3plus) — or an EAAC codec — is recognised but yields `emptyList()`,
 * never a wrong length. Other decodable-in-this-build EA codecs (VAG, N64 VADPCM, DVI IMA,
 * GCADPCM, Xbox IMA) are deliberately outside this ticket's scope and also yield no tracks.
 *
 * Failure modes: a malformed block table or an out-of-range block/header offset logs at `w` and
 * returns `null` (single stream) or skips the offending stream; parsing never throws.
 *
 * Platform is [net.sigmabeta.chipbox.models.Platform.OTHER] for every stream: none of the extensions
 * this reader serves implies a console, and the reader never infers one from [identifier] (a
 * content-source id need not have a file extension).
 *
 * **No real-world SCHl corpus exists**, so parity is best-effort on synthetic fixtures (the
 * single-stream and looping fixtures were cross-checked against the host libvgmstream probe).
 */
class EaSchlReader(private val hatchet: Hatchet) : Reader() {

    override val version: Int = EA_SCHL_VERSION

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        try {
            readTracks(bytesAsReader(bytes), identifier)
        } catch (e: IndexOutOfBoundsException) {
            hatchet.w("EA SCHl parse failed: truncated header for $identifier (${e.message}).")
            null
        }

    private fun readTracks(reader: ByteReader, identifier: String): List<RawTrack>? {
        val headerBlocks = readAudioHeaders(reader)
        if (headerBlocks == null) {
            hatchet.w("EA SCHl parse failed: $identifier is not a valid SCHl block table.")
            return null
        }

        val streams = mutableListOf<EaSchlStream>()
        var unsupportedCodec = false
        for ((index, block) in headerBlocks.withIndex()) {
            val stream = parseHeader(reader, block)
            if (stream == null) {
                hatchet.w("EA SCHl: skipping stream ${index + 1} of $identifier (malformed header).")
                continue
            }
            if (!StreamedCodecs.isCodecAllowed(StreamedCodecs.Format.EA_SCHL, stream.codec)) {
                hatchet.d(
                    "EA SCHl: stream ${index + 1} of $identifier uses codec " +
                        "0x${stream.codec.toString(16)}, which this libvgmstream build cannot decode; " +
                        "no tracks.",
                )
                unsupportedCodec = true
                continue
            }
            streams += stream
        }

        return when {
            unsupportedCodec -> emptyList()

            streams.isEmpty() -> null

            else -> streams.mapIndexed { index, stream ->
                streamedRawTrack(
                    identifier = identifier,
                    lengthMs = stream.lengthMs(),
                    subsongIndex = index,
                )
            }
        }
    }

    /**
     * Walks the top-level block table from offset 0, returning every audio-header block. A file
     * must start with an `SCHl`/`SHxx` header (the standard meta's magic check). Each block's size
     * is read with upstream's `guess_endian32` heuristic (the size field is little-endian except in
     * early Saturn/Mac files), and a size that is impossible — smaller than the 8-byte block header
     * or running past the file — rejects the whole table. The walk stops at `SCEl`, or at the
     * `0x00000000`/`0xFFFFFFFF` words the layout treats as EOF.
     */
    private fun readAudioHeaders(reader: ByteReader): List<AudioHeaderBlock>? {
        if (reader.size < BLOCK_HEADER_SIZE) return null
        if (!isAudioHeaderId(reader.s32be(0) ?: return null)) return null

        val headers = mutableListOf<AudioHeaderBlock>()
        var offset = 0L
        while (offset + BLOCK_HEADER_SIZE <= reader.size.toLong()) {
            val blockId = reader.s32be(offset) ?: return null
            val blockSize = reader.blockSize(offset) ?: return null
            if (blockSize < BLOCK_HEADER_SIZE.toLong() || offset + blockSize > reader.size.toLong()) {
                return null
            }
            if (isAudioHeaderId(blockId)) headers += AudioHeaderBlock(offset, blockSize.toInt())
            if (blockId == BLOCK_END || blockId == BLOCK_EOF_ZERO || blockId == BLOCK_EOF_ONES) break
            offset += blockSize
        }
        return headers.ifEmpty { null }
    }

    /** Parses one header block into a stream, or null when the patches do not form a valid one. */
    private fun parseHeader(reader: ByteReader, block: AudioHeaderBlock): EaSchlStream? {
        val begin = block.offset + BLOCK_HEADER_SIZE
        val end = block.offset + block.size
        val platformEntry = reader.readPlatformEntry(begin, end) ?: return null
        val fields = EaSchlFields()
        val cursor = PatchCursor(reader, platformEntry.nextOffset)
        if (!cursor.readPatches(end, fields)) return null
        return fields.resolve(platformEntry.platform)
    }

    companion object {
        const val EA_SCHL_VERSION = 1
    }
}

/**
 * Extensions the standard SCHl meta accepts (`ea_schl_standard.c:46`) that no other reader owns.
 * Deliberately omitted: `xa` (XaReader), `strm` (StrmReader), `gsf` (PSF family), and the
 * ambiguous `snd`/`r`/`str`/extensionless entries, which upstream's own metas share with other
 * formats. `bnk`/`hdr`/`abk`/`mpf` are different metas that need companion files, not this reader.
 */
internal val EA_SCHL_EXTENSIONS: List<String> = listOf(
    "asf",
    "lasf",
    "chk",
    "eam",
    "exa",
    "sng",
    "aud",
    "sx",
    "stm",
    "hab",
    "xsf",
)

/** One `SCHl`/`SHxx` block: its offset and declared size (header included). */
private class AudioHeaderBlock(val offset: Long, val size: Int)

/** The platform entry at the start of a header, and where the patch list begins. */
private class PlatformEntry(val platform: Int, val nextOffset: Long)

/** Mutable header fields, defaulted to `-1` exactly like upstream's `EA_VERSION_NONE` sentinels. */
private class EaSchlFields {
    var version: Long = NONE
    var codec1: Long = NONE
    var codec2: Long = NONE
    var bitsPerSample: Long = 0
    var channels: Long = 0
    var sampleRate: Long = 0
    var numSamples: Long = 0
    var loopStart: Long = 0
    var loopEnd: Long = 0

    /** Applies upstream's per-platform defaults, then validates what the file still leaves open. */
    fun resolve(platform: Int): EaSchlStream? {
        if (channels > MAX_CHANNELS) return null
        val bigEndian = platform in BIG_ENDIAN_PLATFORMS
        val actualVersion = (if (version == NONE) defaultVersion(platform) else version) ?: return null
        if (codec1 == NONE && actualVersion == VERSION_V0) {
            codec1 = defaultCodec1(platform) ?: return null
        }
        if (codec1 != NONE && codec2 == NONE) codec2 = codec2FromCodec1(codec1, platform, bigEndian)
        if (codec2 == NONE) codec2 = defaultCodec2(platform) ?: return null
        val rate = (if (sampleRate == 0L) defaultSampleRate(platform) else sampleRate) ?: return null

        val looped = loopEnd != 0L
        if (looped && (loopStart < 0L || loopEnd <= loopStart)) return null
        if (numSamples < 0L || rate !in 1L..MAX_SAMPLE_RATE) return null

        return EaSchlStream(
            sampleRate = rate.toInt(),
            numSamples = numSamples,
            loopStart = loopStart,
            loopEnd = loopEnd,
            looped = looped,
            codec = codec2.toInt(),
        )
    }

    private fun codec2FromCodec1(codec1: Long, platform: Int, bigEndian: Boolean): Long = when (codec1) {
        CODEC1_PCM -> when {
            platform == PLATFORM_PC -> if (bitsPerSample == 8L) {
                CODEC2_S8_INT
            } else {
                if (bigEndian) CODEC2_S16BE_INT else CODEC2_S16LE_INT
            }

            bitsPerSample == 8L -> CODEC2_S8

            else -> if (bigEndian) CODEC2_S16BE else CODEC2_S16LE
        }

        CODEC1_IMA -> CODEC2_IMA_INT

        CODEC1_N64 -> CODEC2_N64

        CODEC1_VAG -> CODEC2_VAG

        CODEC1_EAXA -> if (platform == PLATFORM_PC || platform == PLATFORM_MAC) {
            CODEC2_EAXA_INT
        } else {
            CODEC2_EAXA
        }

        CODEC1_MT10 -> CODEC2_MT10

        else -> NONE
    }
}

/**
 * The header's patch stream: upstream's `read_patch` encoding (a type byte, a byte count and that
 * many big-endian value bytes; `0xFF` skips a 32-bit-sized blob) and the patch dispatch table.
 * Any read past the file or unknown patch byte is a miss, never a throw.
 */
private class PatchCursor(private val reader: ByteReader, private var offset: Long) {

    /** Walks patches until the header-end marker or [end]; false when the header is unparseable. */
    fun readPatches(end: Long, fields: EaSchlFields): Boolean {
        while (offset < end) {
            val patchType = u8() ?: return false
            when (patchType) {
                PATCH_END, PATCH_INFO_START -> return true

                PATCH_CODEC1 -> fields.codec1 = readPatch() ?: return false

                PATCH_CODEC2 -> fields.codec2 = readPatch() ?: return false

                PATCH_VERSION -> fields.version = readPatch() ?: return false

                PATCH_BITS_PER_SAMPLE -> fields.bitsPerSample = readPatch() ?: return false

                PATCH_CHANNELS -> fields.channels = readPatch() ?: return false

                PATCH_SAMPLE_RATE -> fields.sampleRate = readPatch() ?: return false

                PATCH_SAMPLE_COUNT -> fields.numSamples = readPatch() ?: return false

                PATCH_LOOP_START -> fields.loopStart = readPatch() ?: return false

                // sx.exe stores loop_end - 1; upstream adds one back (in a 32-bit field).
                PATCH_LOOP_END -> fields.loopEnd = ((readPatch() ?: return false) + 1) and UINT32_MASK

                in SKIPPED_PATCHES -> if (readPatch() == null) return false

                else -> return false
            }
        }
        return true
    }

    /** Upstream's `read_patch`: returns the encoded value, or 0 for a skipped blob. */
    private fun readPatch(): Long? {
        val byteCount = u8() ?: return null
        return when {
            byteCount == PATCH_BLOB_32 -> {
                val blobSize = u32be() ?: return null
                offset += blobSize
                0L
            }

            byteCount > MAX_PATCH_BYTES -> {
                offset += byteCount
                0L
            }

            else -> {
                var value = 0L
                repeat(byteCount) { value = (value shl 8) or (u8()?.toLong() ?: return null) }
                value
            }
        }
    }

    private fun u8(): Int? {
        val value = reader.u8(offset) ?: return null
        offset += 1
        return value
    }

    private fun u32be(): Long? {
        val value = reader.u32be(offset) ?: return null
        offset += 4
        return value
    }
}

/** One audio stream parsed from a header block, in the terms the length formula needs. */
private data class EaSchlStream(
    val sampleRate: Int,
    val numSamples: Long,
    val loopStart: Long,
    val loopEnd: Long,
    val looped: Boolean,
    val codec: Int,
) {
    fun lengthMs(): Long = streamedLengthMs(
        numSamples = numSamples,
        loopFlag = looped,
        loopStartSample = loopStart,
        loopEndSample = loopEnd,
        sampleRate = sampleRate,
    )
}

/** True for `SCHl` and the localized `SHxx` headers (the per-language stream blocks). */
private fun isAudioHeaderId(id: Int): Boolean = id == BLOCK_HEADER || (id and BLOCK_LOC_MASK) == BLOCK_LOC_HEADER

/**
 * The block size at [offset], using upstream's `guess_endian32`: the size is little-endian unless
 * reading it as big-endian yields the smaller (plausible) number.
 */
private fun ByteReader.blockSize(offset: Long): Long? {
    val littleEndian = u32le(offset + 4) ?: return null
    val bigEndian = u32be(offset + 4) ?: return null
    return if (littleEndian > bigEndian) bigEndian else littleEndian
}

/**
 * Reads the platform entry: `PT` + a little-endian platform id (upstream masks the high half, so
 * any trailing bytes are tolerated), or `GSTR` with its extra config word. Some files prefix an
 * unknown word (NBA 2000 PS), which upstream skips once.
 */
private fun ByteReader.readPlatformEntry(begin: Long, end: Long): PlatformEntry? {
    var offset = begin
    var platformId = u32be(offset) ?: return null
    if (platformId.toInt() != ID_GSTR && (platformId.toInt() and BLOCK_LOC_MASK) != ID_PT) {
        offset += 4
        platformId = u32be(offset) ?: return null
    }
    return when {
        platformId.toInt() == ID_GSTR -> {
            val nextOffset = offset + GSTR_ENTRY_SIZE
            if (nextOffset > end) null else PlatformEntry(PLATFORM_GENERIC, nextOffset)
        }

        (platformId.toInt() and BLOCK_LOC_MASK) == ID_PT ->
            PlatformEntry(u16le(offset + 2)?.toInt() ?: return null, offset + PT_ENTRY_SIZE)

        else -> null
    }
}

private fun defaultVersion(platform: Int): Long? = when (platform) {
    PLATFORM_PC, PLATFORM_PSX, PLATFORM_N64, PLATFORM_MAC, PLATFORM_SAT -> VERSION_V0
    PLATFORM_PS2 -> VERSION_V1
    PLATFORM_GC, PLATFORM_XBOX, PLATFORM_GENERIC -> VERSION_V2
    PLATFORM_X360, PLATFORM_PSP, PLATFORM_PS3, PLATFORM_WII, PLATFORM_3DS -> VERSION_V3
    else -> null
}

private fun defaultCodec1(platform: Int): Long? = when (platform) {
    PLATFORM_PC, PLATFORM_MAC, PLATFORM_SAT -> CODEC1_PCM
    PLATFORM_PSX -> CODEC1_VAG
    PLATFORM_N64 -> CODEC1_N64
    else -> null
}

private fun defaultCodec2(platform: Int): Long? = when (platform) {
    PLATFORM_GENERIC, PLATFORM_PC, PLATFORM_MAC, PLATFORM_X360, PLATFORM_PSP, PLATFORM_PS3 -> CODEC2_EAXA
    PLATFORM_PSX, PLATFORM_PS2 -> CODEC2_VAG
    PLATFORM_N64 -> CODEC2_N64
    PLATFORM_GC -> CODEC2_S16BE
    PLATFORM_XBOX -> CODEC2_S16LE
    PLATFORM_WII, PLATFORM_3DS -> CODEC2_GCADPCM
    else -> null
}

private fun defaultSampleRate(platform: Int): Long? = when (platform) {
    PLATFORM_GENERIC -> 48_000L
    PLATFORM_PC, PLATFORM_PSX, PLATFORM_N64, PLATFORM_MAC, PLATFORM_SAT, PLATFORM_PS2, PLATFORM_PSP -> 22_050L
    PLATFORM_GC, PLATFORM_XBOX -> 24_000L
    PLATFORM_X360, PLATFORM_PS3 -> 44_100L
    PLATFORM_WII, PLATFORM_3DS -> 32_000L
    else -> null
}

private fun ByteReader.u8(offset: Long): Int? {
    if (offset < 0L || offset + 1 > size.toLong()) return null
    return get(offset.toInt()).toInt() and BYTE_MASK
}

private fun ByteReader.u16le(offset: Long): Long? {
    if (offset < 0L || offset + SHORT_SIZE > size.toLong()) return null
    return readShortLeAt(offset.toInt()).toLong() and UINT16_MASK
}

private fun ByteReader.u32le(offset: Long): Long? {
    if (offset < 0L || offset + INT_SIZE > size.toLong()) return null
    return readIntLeAt(offset.toInt()).toLong() and UINT32_MASK
}

private fun ByteReader.s32be(offset: Long): Int? {
    if (offset < 0L || offset + INT_SIZE > size.toLong()) return null
    return readIntBeAt(offset.toInt())
}

private fun ByteReader.u32be(offset: Long): Long? = s32be(offset)?.toLong()?.and(UINT32_MASK)

private const val BLOCK_HEADER_SIZE = 8L
private const val BLOCK_HEADER = 0x5343486C // "SCHl"
private const val BLOCK_END = 0x5343456C // "SCEl"
private const val BLOCK_LOC_HEADER = 0x53480000 // "SH" + language
private const val BLOCK_LOC_MASK = 0xFFFF0000.toInt()
private const val BLOCK_EOF_ZERO = 0x00000000
private const val BLOCK_EOF_ONES = -1 // 0xFFFFFFFF
private const val ID_GSTR = 0x47535452 // "GSTR"
private const val ID_PT = 0x50540000 // "PT\0\0"

/** EA platform ids (`meta/ea_schl.c:16-36`). */
private const val PLATFORM_PC = 0x00
private const val PLATFORM_PSX = 0x01
private const val PLATFORM_N64 = 0x02
private const val PLATFORM_MAC = 0x03
private const val PLATFORM_SAT = 0x04
private const val PLATFORM_PS2 = 0x05
private const val PLATFORM_GC = 0x06
private const val PLATFORM_XBOX = 0x07
private const val PLATFORM_GENERIC = 0x08
private const val PLATFORM_X360 = 0x09
private const val PLATFORM_PSP = 0x0A
private const val PLATFORM_PS3 = 0x0E
private const val PLATFORM_WII = 0x10
private const val PLATFORM_3DS = 0x14

/** Platforms whose blocks/codecs are big-endian (`meta/ea_schl.c:830-839`). */
private val BIG_ENDIAN_PLATFORMS = setOf(
    PLATFORM_N64,
    PLATFORM_MAC,
    PLATFORM_SAT,
    PLATFORM_GC,
    PLATFORM_X360,
    PLATFORM_PS3,
    PLATFORM_WII,
    PLATFORM_GENERIC,
)

private const val GSTR_ENTRY_SIZE = 8L
private const val PT_ENTRY_SIZE = 4L

/** Header versions (`meta/ea_schl.c:9-13`); [NONE] is the "absent" sentinel. */
private const val NONE = -1L
private const val VERSION_V0 = 0L
private const val VERSION_V1 = 1L
private const val VERSION_V2 = 2L
private const val VERSION_V3 = 3L

/** Codec1 ids (early files) and the codec2 ids they collapse to (`meta/ea_schl.c:40-75`). */
private const val CODEC1_PCM = 0x00L
private const val CODEC1_IMA = 0x02L
private const val CODEC1_N64 = 0x05L
private const val CODEC1_VAG = 0x06L
private const val CODEC1_EAXA = 0x07L
private const val CODEC1_MT10 = 0x09L

private const val CODEC2_S16LE_INT = 0x00L
private const val CODEC2_S16BE_INT = 0x01L
private const val CODEC2_S8_INT = 0x02L
private const val CODEC2_EAXA_INT = 0x03L
private const val CODEC2_MT10 = 0x04L
private const val CODEC2_VAG = 0x05L
private const val CODEC2_N64 = 0x06L
private const val CODEC2_S16BE = 0x07L
private const val CODEC2_S16LE = 0x08L
private const val CODEC2_S8 = 0x09L
private const val CODEC2_EAXA = 0x0AL
private const val CODEC2_IMA_INT = 0x0DL
private const val CODEC2_GCADPCM = 0x12L

/** Header patch types (`meta/ea_schl.c:642-818`). */
private const val PATCH_SAMPLE_RATE = 0x84
private const val PATCH_SAMPLE_COUNT = 0x85
private const val PATCH_LOOP_START = 0x86
private const val PATCH_LOOP_END = 0x87
private const val PATCH_CODEC1 = 0x83
private const val PATCH_CODEC2 = 0xA0
private const val PATCH_VERSION = 0x80
private const val PATCH_BITS_PER_SAMPLE = 0x81
private const val PATCH_CHANNELS = 0x82
private const val PATCH_END = 0xFF
private const val PATCH_INFO_START = 0xFE
private const val PATCH_BLOB_32 = 0xFF
private const val MAX_PATCH_BYTES = 4

/** Patch types whose payload is decoded and discarded (offsets, coefficients, playback flags). */
private val SKIPPED_PATCHES = setOf(
    0x00,
    0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15,
    0x19,
    0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A,
    0x88, 0x89, 0x94, 0x95, 0xA2, 0xA3,
    0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0x8F, 0x90, 0x91, 0x92, 0x93,
    0x98, 0x99, 0x9C, 0x9D, 0x9E, 0x9F, 0xA1, 0xA6, 0xA7, 0xAB, 0xAC, 0xAD,
    0xFC, 0xFD,
)

private const val MAX_CHANNELS = 6L
private const val MAX_SAMPLE_RATE = 2_147_483_647L
private const val BYTE_MASK = 0xFF
private const val SHORT_SIZE = 2L
private const val INT_SIZE = 4L
private const val UINT16_MASK = 0xFFFFL
