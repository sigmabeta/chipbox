package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * Nintendo 3DS BCSTM (`.bcstm`). Port of vgmstream's `meta/bcstm.c`.
 *
 * The file is a little-endian `CSTM` section table whose `INFO` section holds a stream-info record
 * at +0x20: codec, loop flag, channel count, rate, loop start and sample count. Unlike
 * BRSTM/BFSTM the whole container — magic aside — is little-endian, so the BOM check reads a
 * 16-bit LE value and the section ids/offsets/stream fields are all LE. The stream's loop end is
 * its sample count, as upstream sets `loop_end_sample = num_samples`.
 *
 * Upstream has one special case: a DSP-codec file without a `SEEK` section is Camelot's IMA
 * hijack (Mario Tennis Open / Mario Golf: World Tour), whose audio starts at the seek offset. The
 * metadata is unchanged, so this reader only mirrors the shape of the check: without SEEK the file
 * must still have a usable seek offset, otherwise it is a parse miss.
 *
 * There is exactly one stream info per BCSTM (the format's multi-track table selects channel
 * groups of that stream, not separate subsongs), so this reader emits one track.
 */
class BcstmReader(private val hatchet: Hatchet) : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        try {
            val streams = readStreams(bytes)
            when {
                streams == null -> {
                    hatchet.w("BCSTM parse failed: not a valid CSTM stream.")
                    null
                }

                streams.isEmpty() -> {
                    hatchet.d("BCSTM: parsed a valid header but it declares no channels.")
                    emptyList()
                }

                else -> streams.mapIndexed { index, stream ->
                    dspTrack(identifier, index, stream)
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            hatchet.w("BCSTM parse failed: truncated header (${e.message}).")
            null
        } catch (e: IllegalArgumentException) {
            hatchet.w("BCSTM parse failed: ${e.message}.")
            null
        }

    private fun readStreams(bytes: ByteArray): List<DspStreamInfo>? {
        if (!isBcstm(bytes)) return null
        val reader = bytesAsReader(bytes)
        val layout = readLayout(reader) ?: return null
        return streamsAt(reader, layout)
    }

    private fun isBcstm(bytes: ByteArray): Boolean =
        bytes.size >= MIN_HEADER_SIZE &&
            bytes.hasAsciiPrefix(CSTM_MAGIC) &&
            (bytesAsReader(bytes).readShortLeAt(OFFSET_BOM).toInt() and SHORT_MASK) == BOM_LITTLE_ENDIAN

    /** Resolves the section table: INFO gives the stream info, SEEK/DATA locate the payload. */
    private fun readLayout(reader: ByteReader): Layout? {
        val sectionCount = reader.readU16(OFFSET_SECTION_COUNT, bigEndian = false)
        var infoOffset = 0
        var seekOffset = 0
        var dataOffset = 0
        for (index in 0 until sectionCount) {
            val entry = OFFSET_SECTIONS + index * SECTION_ENTRY_SIZE
            val id = reader.readU16(entry, bigEndian = false)
            val offset = reader.readU32(entry + SECTION_OFFSET_DELTA, bigEndian = false).toInt()
            when (id) {
                SECTION_INFO -> infoOffset = offset
                SECTION_SEEK -> seekOffset = offset
                SECTION_DATA -> dataOffset = offset
                else -> Unit
            }
        }
        if (infoOffset == 0) return null
        return Layout(infoOffset, seekOffset, dataOffset)
    }

    private fun streamsAt(reader: ByteReader, layout: Layout): List<DspStreamInfo>? {
        val header = readHeader(reader, layout) ?: return null
        return when {
            header.channels == 0 -> emptyList()
            !header.isValid() -> null
            !header.hasAudioSection(reader, layout) -> null
            else -> listOf(header.toStreamInfo())
        }
    }

    private fun readHeader(reader: ByteReader, layout: Layout): BcstmHeader? {
        val info = layout.infoOffset
        if (!reader.hasAsciiAt(info, INFO_MAGIC)) return null
        return BcstmHeader(
            codec = reader.get(info + INFO_CODEC).toInt() and BYTE_MASK,
            loopFlag = reader.get(info + INFO_LOOP_FLAG).toInt() and BYTE_MASK,
            channels = reader.get(info + INFO_CHANNELS).toInt() and BYTE_MASK,
            sampleRate = reader.readS32(info + INFO_SAMPLE_RATE, bigEndian = false),
            loopStart = reader.readS32(info + INFO_LOOP_START, bigEndian = false),
            numSamples = reader.readS32(info + INFO_NUM_SAMPLES, bigEndian = false),
        )
    }

    private fun BcstmHeader.isValid(): Boolean = listOf(
        codec in SUPPORTED_CODECS,
        channels in 1..MAX_CHANNELS,
        sampleRate > 0,
    ).all { it }

    /**
     * Upstream's audio-data branch: normally DATA must exist; a DSP-codec file whose SEEK section
     * is absent is Camelot's IMA variant, which starts at the seek offset instead.
     */
    private fun BcstmHeader.hasAudioSection(reader: ByteReader, layout: Layout): Boolean =
        if (codec == CODEC_DSP && !reader.hasAsciiAt(layout.seekOffset, SEEK_MAGIC)) {
            layout.seekOffset != 0
        } else {
            layout.dataOffset != 0
        }

    private fun BcstmHeader.toStreamInfo(): DspStreamInfo = DspStreamInfo(
        sampleRate = sampleRate,
        loopFlag = loopFlag != 0,
        loopStartSample = loopStart.toLong(),
        // The container has no loop-end field; upstream treats the whole stream as the loop.
        loopEndSample = numSamples.toLong(),
        numSamples = numSamples.toLong(),
    )

    private data class Layout(
        val infoOffset: Int,
        val seekOffset: Int,
        val dataOffset: Int,
    )

    private data class BcstmHeader(
        val codec: Int,
        val loopFlag: Int,
        val channels: Int,
        val sampleRate: Int,
        val loopStart: Int,
        val numSamples: Int,
    )

    companion object {
        private const val CSTM_MAGIC = "CSTM"
        private const val INFO_MAGIC = "INFO"
        private const val SEEK_MAGIC = "SEEK"
        private const val MIN_HEADER_SIZE = 0x14
        private const val SHORT_MASK = 0xFFFF
        private const val BYTE_MASK = 0xFF

        private const val BOM_LITTLE_ENDIAN = 0xFEFF

        private const val OFFSET_BOM = 0x04
        private const val OFFSET_SECTION_COUNT = 0x10
        private const val OFFSET_SECTIONS = 0x14
        private const val SECTION_ENTRY_SIZE = 0x0C
        private const val SECTION_OFFSET_DELTA = 0x04
        private const val SECTION_INFO = 0x4000
        private const val SECTION_SEEK = 0x4001
        private const val SECTION_DATA = 0x4002

        private const val INFO_CODEC = 0x20
        private const val INFO_LOOP_FLAG = 0x21
        private const val INFO_CHANNELS = 0x22
        private const val INFO_SAMPLE_RATE = 0x24
        private const val INFO_LOOP_START = 0x28
        private const val INFO_NUM_SAMPLES = 0x2C

        private const val CODEC_DSP = 2
        private val SUPPORTED_CODECS = setOf(0, 1, 2) // PCM8, PCM16LE, NGC DSP (or Camelot IMA)
        private const val MAX_CHANNELS = 64
    }
}
