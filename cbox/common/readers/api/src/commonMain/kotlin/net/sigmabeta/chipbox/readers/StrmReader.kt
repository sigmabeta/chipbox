package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * Nintendo DS STRM streamed audio (`cbox/native/vgmstream/Core/vgmstream/src/meta/nds_strm.c`).
 *
 * A fixed little-endian header: the byte-order mark, a `HEAD` chunk, then codec / loop flag /
 * channels / sample rate / loop start / sample count. Only the metadata is read here; playback is
 * still vgmstream's job. The format carries no stream name, so the title falls back to
 * [TAG_UNKNOWN] and the scanner derives it from the filename (as the native probe's blank
 * `stream_name` did).
 *
 * Length policy (loops, fade, integer millisecond truncation) lives in [streamedLengthMs].
 */
class StrmReader(private val hatchet: Hatchet) : Reader() {

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        readStreamedTracks("STRM", identifier, hatchet) {
            val lengthMs = parseLengthMs(bytesAsReader(bytes))
            if (lengthMs == null) {
                hatchet.w("STRM parse failed: unrecognised or truncated header for $identifier.")
                null
            } else {
                listOf(streamedRawTrack(identifier = identifier, lengthMs = lengthMs))
            }
        }

    /** Returns the probe length in ms, or null when the header is not a STRM this reader accepts. */
    private fun parseLengthMs(buf: ByteReader): Long? {
        if (!buf.hasAsciiPrefix(MAGIC)) return null

        val bom = buf.readIntBeAt(OFFSET_BOM).toLong() and UINT32_MASK
        val headOk = buf.hasAsciiAt(OFFSET_HEAD, HEAD_MAGIC)
        // Upstream guards with `!HEAD && size != 0x50` (a bug: either alone passes). The prototype
        // required both, matched all 62 corpus files, and is the stricter reading; keep it.
        val headSize = buf.readIntLeAt(OFFSET_HEAD_SIZE).toLong() and UINT32_MASK
        val codec = buf.get(OFFSET_CODEC).toInt() and UINT8_MASK
        val loopFlag = buf.get(OFFSET_LOOP_FLAG).toInt() != 0
        val channels = buf.get(OFFSET_CHANNELS).toInt() and UINT8_MASK
        val sampleRate = buf.readShortLeAt(OFFSET_SAMPLE_RATE).toInt() and UINT16_MASK
        val loopStart = buf.readIntLeAt(OFFSET_LOOP_START).toLong() and UINT32_MASK
        val numSamples = buf.readIntLeAt(OFFSET_NUM_SAMPLES).toLong() and UINT32_MASK

        val chunkOk = (bom == BOM_LE || bom == BOM_BE) && headOk && headSize == HEAD_SIZE
        val formatOk = codec in CODEC_PCM8..CODEC_IMA && channels in 1..MAX_CHANNELS && sampleRate > 0
        if (!chunkOk || !formatOk) return null

        return streamedLengthMs(
            numSamples = numSamples,
            loopFlag = loopFlag,
            loopStartSample = loopStart,
            loopEndSample = numSamples,
            sampleRate = sampleRate,
        )
    }

    private companion object {
        const val MAGIC = "STRM"
        const val HEAD_MAGIC = "HEAD"

        const val OFFSET_BOM = 0x04
        const val OFFSET_HEAD = 0x10
        const val OFFSET_HEAD_SIZE = 0x14
        const val OFFSET_CODEC = 0x18
        const val OFFSET_LOOP_FLAG = 0x19
        const val OFFSET_CHANNELS = 0x1A
        const val OFFSET_SAMPLE_RATE = 0x1C
        const val OFFSET_LOOP_START = 0x20
        const val OFFSET_NUM_SAMPLES = 0x24

        /** `0xFFFE0001` or `0xFEFF0001` — upstream's "newer games" second BOM. */
        const val BOM_LE = 0xFFFE0001L
        const val BOM_BE = 0xFEFF0001L
        const val HEAD_SIZE = 0x50L

        const val CODEC_PCM8 = 0 // coding_PCM8
        const val CODEC_IMA = 2 // coding_NDS_IMA
        const val MAX_CHANNELS = 2

        const val UINT8_MASK = 0xFF
        const val UINT16_MASK = 0xFFFF
    }
}
