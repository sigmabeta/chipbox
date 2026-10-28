package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * Nintendo Wii U / Switch BFSTM (`.bfstm`). Port of vgmstream's `meta/bfstm.c`.
 *
 * The file is an `FSTM` section table whose `INFO` section holds a stream-info record at +0x20:
 * codec, loop flag, channel count, rate, loop start and sample count. The Wii U form
 * is big-endian (BOM `0xFEFF`); Switch rips are little-endian (BOM `0xFFFE`), so every field after
 * the magic is read in the BOM's endianness. The stream's loop end is its sample count, as upstream
 * sets `loop_end_sample = num_samples`.
 *
 * One track per file. The vendored `meta/bfstm.c` never sets `num_streams`, so the native probe
 * exposed one subsong even when newer BFSTMs carry a `REGN` section: regions describe layout, not
 * separately playable streams. Emitting the whole file as the reference stream matches the probe.
 *
 * Codec 0x03 (IMA) is rejected exactly like upstream; PCM8/PCM16/NGC-DSP are all built-in decoders
 * in the vendored build, so no allow-list entry is needed.
 */
class BfstmReader(private val hatchet: Hatchet) : Reader() {

    /** 2: one track per file — the earlier version emitted one extra track per REGN region. */
    override val version: Int = BFSTM_VERSION
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        try {
            val streams = readStreams(bytes)
            when {
                streams == null -> {
                    hatchet.w("BFSTM parse failed: not a valid FSTM stream.")
                    null
                }

                streams.isEmpty() -> {
                    hatchet.d("BFSTM: parsed a valid header but it declares no channels.")
                    emptyList()
                }

                else -> streams.mapIndexed { index, stream ->
                    dspTrack(identifier, index, stream)
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            hatchet.w("BFSTM parse failed: truncated header (${e.message}).")
            null
        } catch (e: IllegalArgumentException) {
            hatchet.w("BFSTM parse failed: ${e.message}.")
            null
        }

    private fun readStreams(bytes: ByteArray): List<DspStreamInfo>? {
        if (!isBfstm(bytes)) return null
        val reader = bytesAsReader(bytes)
        val layout = readLayout(reader) ?: return null
        return streamsAt(reader, layout)
    }

    private fun isBfstm(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_HEADER_SIZE || !bytes.hasAsciiPrefix(FSTM_MAGIC)) return false
        val bom = bytesAsReader(bytes).readShortBeAt(OFFSET_BOM).toInt() and SHORT_MASK
        return bom == BOM_BIG_ENDIAN || bom == BOM_LITTLE_ENDIAN
    }

    /** Resolves the section table: INFO gives the stream info, DATA the payload. */
    private fun readLayout(reader: ByteReader): Layout? {
        val bigEndian = (reader.readShortBeAt(OFFSET_BOM).toInt() and SHORT_MASK) == BOM_BIG_ENDIAN
        val sectionCount = reader.readU16(OFFSET_SECTION_COUNT, bigEndian)
        var infoOffset = 0
        var dataOffset = 0
        for (index in 0 until sectionCount) {
            val entry = OFFSET_SECTIONS + index * SECTION_ENTRY_SIZE
            val id = reader.readU16(entry, bigEndian)
            val offset = reader.readU32(entry + SECTION_OFFSET_DELTA, bigEndian).toInt()
            when (id) {
                SECTION_INFO -> infoOffset = offset

                SECTION_DATA -> dataOffset = offset

                // SEEK, REGN and PDAT do not affect the one playable stream.
                else -> Unit
            }
        }
        // Upstream requires both sections.
        if (infoOffset == 0 || dataOffset == 0) return null
        return Layout(bigEndian, infoOffset)
    }

    private fun streamsAt(reader: ByteReader, layout: Layout): List<DspStreamInfo>? {
        val header = readHeader(reader, layout) ?: return null
        return when {
            header.channels == 0 -> emptyList()
            !header.isValid() -> null
            else -> listOf(header.toStreamInfo())
        }
    }

    private fun readHeader(reader: ByteReader, layout: Layout): BfstmHeader? {
        val info = layout.infoOffset
        if (!reader.hasAsciiAt(info, INFO_MAGIC)) return null
        return BfstmHeader(
            codec = reader.get(info + INFO_CODEC).toInt() and BYTE_MASK,
            loopFlag = reader.get(info + INFO_LOOP_FLAG).toInt() and BYTE_MASK,
            channels = reader.get(info + INFO_CHANNELS).toInt() and BYTE_MASK,
            sampleRate = reader.readU32(info + INFO_SAMPLE_RATE, layout.bigEndian).toInt(),
            loopStart = reader.readS32(info + INFO_LOOP_START, layout.bigEndian),
            numSamples = reader.readS32(info + INFO_NUM_SAMPLES, layout.bigEndian),
        )
    }

    private fun BfstmHeader.isValid(): Boolean = listOf(
        codec in SUPPORTED_CODECS,
        channels in 1..MAX_CHANNELS,
        sampleRate > 0,
    ).all { it }

    private fun BfstmHeader.toStreamInfo(): DspStreamInfo = DspStreamInfo(
        sampleRate = sampleRate,
        loopFlag = loopFlag != 0,
        loopStartSample = loopStart.toLong(),
        // The container has no loop-end field; upstream treats the whole stream as the loop.
        loopEndSample = numSamples.toLong(),
        numSamples = numSamples.toLong(),
    )

    private data class Layout(
        val bigEndian: Boolean,
        val infoOffset: Int,
    )

    private data class BfstmHeader(
        val codec: Int,
        val loopFlag: Int,
        val channels: Int,
        val sampleRate: Int,
        val loopStart: Int,
        val numSamples: Int,
    )

    companion object {
        private const val BFSTM_VERSION = 2
        private const val FSTM_MAGIC = "FSTM"
        private const val INFO_MAGIC = "INFO"
        private const val MIN_HEADER_SIZE = 0x14
        private const val SHORT_MASK = 0xFFFF
        private const val BYTE_MASK = 0xFF

        private const val BOM_BIG_ENDIAN = 0xFEFF
        private const val BOM_LITTLE_ENDIAN = 0xFFFE

        private const val OFFSET_BOM = 0x04
        private const val OFFSET_SECTION_COUNT = 0x10
        private const val OFFSET_SECTIONS = 0x14
        private const val SECTION_ENTRY_SIZE = 0x0C
        private const val SECTION_OFFSET_DELTA = 0x04
        private const val SECTION_INFO = 0x4000
        private const val SECTION_DATA = 0x4002

        private const val INFO_CODEC = 0x20
        private const val INFO_LOOP_FLAG = 0x21
        private const val INFO_CHANNELS = 0x22
        private const val INFO_SAMPLE_RATE = 0x24
        private const val INFO_LOOP_START = 0x28
        private const val INFO_NUM_SAMPLES = 0x2C

        private val SUPPORTED_CODECS = setOf(0, 1, 2) // PCM8, PCM16, NGC DSP
        private const val MAX_CHANNELS = 64
    }
}
