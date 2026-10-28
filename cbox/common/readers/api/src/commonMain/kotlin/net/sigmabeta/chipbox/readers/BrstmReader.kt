package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * Nintendo Wii BRSTM (`.brstm`, plus the fake `.brstmspm` extension for Super Paper Mario rips).
 * Port of vgmstream's `meta/brstm.c`.
 *
 * The file is a chunk table (`RSTM`) whose `HEAD` chunk holds one stream-info record: codec,
 * loop flag, channel count, rate, loop start and the stream's sample count. The record's loop end
 * is the sample count, exactly as upstream sets `loop_end_sample = num_samples` (the container
 * carries only a loop start; there is no separate loop-end field).
 *
 * Upstream reads a single stream. Its two header layouts are honoured: the normal 1.0 chunk table
 * (`info_offset = head_offset + 0x20`) and the compact 0.1 header (`head_offset = head_size`,
 * `info_offset = head_offset + 0x08`).
 *
 * The container declares its exact file size at 0x08; upstream rejects a mismatch, which is how a
 * truncated rip becomes a parse miss rather than an over-long track.
 *
 * BRSTM's `HEAD` chunk also carries a track table, but upstream ignores it: those entries select
 * channel groups of one stream, not separate subsongs, so this reader emits one track.
 */
class BrstmReader(
    private val hatchet: Hatchet,
    private val superPaperMario: Boolean = false,
) : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        try {
            val streams = readStreams(bytes)
            when {
                streams == null -> {
                    hatchet.w("BRSTM parse failed: not a valid RSTM stream.")
                    null
                }

                streams.isEmpty() -> {
                    hatchet.d("BRSTM: parsed a valid header but it declares no channels.")
                    emptyList()
                }

                else -> streams.mapIndexed { index, stream ->
                    dspTrack(identifier, index, stream)
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            hatchet.w("BRSTM parse failed: truncated header (${e.message}).")
            null
        } catch (e: IllegalArgumentException) {
            hatchet.w("BRSTM parse failed: ${e.message}.")
            null
        }

    private fun readStreams(bytes: ByteArray): List<DspStreamInfo>? {
        if (!isBrstm(bytes)) return null
        val reader = bytesAsReader(bytes)
        // Upstream requires the declared file size to match exactly, so a truncated or
        // trailing-junk file is a parse miss.
        if (reader.readIntBeAt(OFFSET_FILE_SIZE) != bytes.size) return null
        return streamsAt(reader)
    }

    private fun isBrstm(bytes: ByteArray): Boolean =
        bytes.size >= MIN_HEADER_SIZE &&
            bytes.hasAsciiPrefix(RSTM_MAGIC) &&
            (bytesAsReader(bytes).readShortBeAt(OFFSET_BOM).toInt() and SHORT_MASK) == BOM_BIG_ENDIAN

    private fun streamsAt(reader: ByteReader): List<DspStreamInfo>? {
        val header = readHeader(reader) ?: return null
        return when {
            header.channels == 0 -> emptyList()
            header.isValid() -> listOf(header.toStreamInfo())
            else -> null
        }
    }

    private fun readHeader(reader: ByteReader): BrstmHeader? {
        val version = reader.readShortBeAt(OFFSET_VERSION).toInt() and SHORT_MASK
        val headOffset = if (version == VERSION_LEGACY) {
            reader.readShortBeAt(OFFSET_HEAD_SIZE).toInt() and SHORT_MASK
        } else {
            reader.readIntBeAt(OFFSET_HEAD_OFFSET)
        }
        if (!reader.hasAsciiAt(headOffset, HEAD_MAGIC)) return null

        val infoOffset = if (version == VERSION_LEGACY) {
            headOffset + LEGACY_INFO_DELTA
        } else {
            headOffset + STREAM_INFO_DELTA
        }
        return BrstmHeader(
            codec = reader.get(infoOffset + INFO_CODEC).toInt() and BYTE_MASK,
            loopFlag = reader.get(infoOffset + INFO_LOOP_FLAG).toInt() and BYTE_MASK,
            channels = reader.get(infoOffset + INFO_CHANNELS).toInt() and BYTE_MASK,
            sampleRate = reader.readShortBeAt(infoOffset + INFO_SAMPLE_RATE).toInt() and SHORT_MASK,
            loopStart = reader.readIntBeAt(infoOffset + INFO_LOOP_START),
            numSamples = reader.readIntBeAt(infoOffset + INFO_NUM_SAMPLES),
        )
    }

    private fun BrstmHeader.isValid(): Boolean = listOf(
        codec in SUPPORTED_CODECS,
        channels in 1..MAX_CHANNELS,
        sampleRate > 0,
    ).all { it }

    private fun BrstmHeader.toStreamInfo(): DspStreamInfo {
        // Most Super Paper Mario tracks carry 44.1 kHz but play at 22.05 kHz; upstream detects
        // this from the fake brstmspm extension.
        val effectiveRate = if (superPaperMario && sampleRate == SUPER_PAPER_MARIO_RATE) {
            SUPER_PAPER_MARIO_RATE / 2
        } else {
            sampleRate
        }
        return DspStreamInfo(
            sampleRate = effectiveRate,
            loopFlag = loopFlag != 0,
            loopStartSample = loopStart.toLong(),
            // The container has no loop-end field; upstream treats the whole stream as the loop.
            loopEndSample = numSamples.toLong(),
            numSamples = numSamples.toLong(),
        )
    }

    private data class BrstmHeader(
        val codec: Int,
        val loopFlag: Int,
        val channels: Int,
        val sampleRate: Int,
        val loopStart: Int,
        val numSamples: Int,
    )

    companion object {
        private const val RSTM_MAGIC = "RSTM"
        private const val HEAD_MAGIC = "HEAD"
        private const val MIN_HEADER_SIZE = 0x40
        private const val BOM_BIG_ENDIAN = 0xFEFF
        private const val SHORT_MASK = 0xFFFF
        private const val BYTE_MASK = 0xFF

        private const val OFFSET_BOM = 0x04
        private const val OFFSET_VERSION = 0x06
        private const val OFFSET_FILE_SIZE = 0x08
        private const val OFFSET_HEAD_SIZE = 0x0C
        private const val OFFSET_HEAD_OFFSET = 0x10

        private const val VERSION_LEGACY = 0x0001
        private const val LEGACY_INFO_DELTA = 0x08
        private const val STREAM_INFO_DELTA = 0x20

        private const val INFO_CODEC = 0x00
        private const val INFO_LOOP_FLAG = 0x01
        private const val INFO_CHANNELS = 0x02
        private const val INFO_SAMPLE_RATE = 0x04
        private const val INFO_LOOP_START = 0x08
        private const val INFO_NUM_SAMPLES = 0x0C

        private val SUPPORTED_CODECS = setOf(0, 1, 2) // PCM8, PCM16BE, NGC DSP
        private const val MAX_CHANNELS = 64
        private const val SUPER_PAPER_MARIO_RATE = 44_100
    }
}
