package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * GENH (`cbox/native/vgmstream/Core/vgmstream/src/meta/genh.c`) — vgmstream's generic wrapper
 * around a headerless stream: channels, rate, loop points, codec, data offset and header size,
 * with an optional extended block carrying the explicit sample count and data size.
 *
 * Only the metadata is read here (playback stays vgmstream's job). Codecs whose `genh.c` case is
 * behind a `VGM_USE_*` guard — MPEG, ATRAC3/ATRAC3plus, XMA1/2, FFmpeg, AC3 and AAC — are
 * recognised but unsupported: the file yields `emptyList()` rather than a length this build cannot
 * play (see [StreamedCodecs]). GENH carries no stream name, so the title falls back to
 * [TAG_UNKNOWN] and the scanner derives it from the filename, exactly as the native probe's blank
 * `stream_name` did.
 *
 * Length policy (loops, fade, integer millisecond truncation) lives in [streamedLengthMs].
 */
class GenhReader(private val hatchet: Hatchet) : Reader() {

    /** 2: the codec gate turns previously-emitted tracks for external codecs into no tracks. */
    override val version: Int = GENH_VERSION

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        readStreamedTracks("GENH", identifier, hatchet) {
            val buf = bytesAsReader(bytes)
            val lengthMs = parseLengthMs(buf)
            when {
                lengthMs == null -> {
                    hatchet.w("GENH parse failed: unrecognised or truncated header for $identifier.")
                    null
                }

                !StreamedCodecs.isCodecAllowed(StreamedCodecs.Format.GENH, buf.readIntLeAt(OFFSET_CODEC)) -> {
                    hatchet.d(
                        "GENH: $identifier uses a codec this libvgmstream build cannot decode; " +
                            "no tracks.",
                    )
                    emptyList()
                }

                else -> listOf(streamedRawTrack(identifier = identifier, lengthMs = lengthMs))
            }
        }

    /** Returns the probe length in ms, or null when the header is not a GENH this reader accepts. */
    private fun parseLengthMs(buf: ByteReader): Long? {
        if (!buf.hasAsciiPrefix(MAGIC)) return null

        val channels = buf.readIntLeAt(OFFSET_CHANNELS)
        val sampleRate = buf.readIntLeAt(OFFSET_SAMPLE_RATE)
        val loopStart = buf.readIntLeAt(OFFSET_LOOP_START).toLong()
        val loopEnd = buf.readIntLeAt(OFFSET_LOOP_END).toLong()

        // Old GENH headers left headerSize at 0: upstream then treats both offsets as 0x800.
        var startOffset = buf.readIntLeAt(OFFSET_START_OFFSET).toLong() and UINT32_MASK
        var headerSize = buf.readIntLeAt(OFFSET_HEADER_SIZE).toLong() and UINT32_MASK
        if (headerSize == 0L) {
            startOffset = OLD_HEADER_SIZE
            headerSize = OLD_HEADER_SIZE
        }

        var numSamples = 0L
        var dataSize = 0L
        if (headerSize >= EXTENDED_HEADER_SIZE) {
            numSamples = buf.readIntLeAt(OFFSET_NUM_SAMPLES).toLong()
            dataSize = buf.readIntLeAt(OFFSET_DATA_SIZE).toLong() and UINT32_MASK
        }
        // Upstream's data-size fallback: a zero field means "rest of the file". For the built-in
        // codecs this value never feeds the length (only FFmpeg-backed codecs consumed it), so it
        // serves here as the payload/truncation check.
        if (dataSize == 0L) dataSize = buf.size - startOffset
        if (numSamples <= 0L) numSamples = loopEnd

        val headerOk = headerSize <= startOffset && headerSize >= MIN_HEADER_SIZE
        val streamOk = channels >= 1 && sampleRate > 0
        val payloadOk = startOffset <= buf.size && dataSize > 0L
        if (!headerOk || !streamOk || !payloadOk) return null

        return streamedLengthMs(
            numSamples = numSamples,
            loopFlag = loopStart != NO_LOOP_START,
            loopStartSample = loopStart,
            loopEndSample = loopEnd,
            sampleRate = sampleRate,
        )
    }

    private companion object {
        const val GENH_VERSION = 2
        const val MAGIC = "GENH"

        const val OFFSET_CHANNELS = 0x04
        const val OFFSET_SAMPLE_RATE = 0x0C
        const val OFFSET_LOOP_START = 0x10
        const val OFFSET_LOOP_END = 0x14
        const val OFFSET_CODEC = 0x18
        const val OFFSET_START_OFFSET = 0x1C
        const val OFFSET_HEADER_SIZE = 0x20
        const val OFFSET_NUM_SAMPLES = 0x40
        const val OFFSET_DATA_SIZE = 0x50

        /** `loop_start_sample == -1` is upstream's "no loop" sentinel; any other value loops. */
        const val NO_LOOP_START = -1L
        const val MIN_HEADER_SIZE = 0x24L
        const val EXTENDED_HEADER_SIZE = 0x100L
        const val OLD_HEADER_SIZE = 0x800L
    }
}
