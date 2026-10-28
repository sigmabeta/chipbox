package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * Standalone GameCube/Wii DSP (`.dsp`): a 0x60-byte DSPADPCM header followed by NGC DSP frames.
 * Port of vgmstream's `meta/ngc_dsp_std.c` (`init_vgmstream_ngc_dsp_std` /
 * `init_vgmstream_ngc_dsp_std_le`, via `read_dsp_header_endian`) and
 * `coding/ngc_dsp_decoder.c`'s `dsp_nibbles_to_samples`.
 *
 * The header's own sample count is the play length; loop points are nibble offsets converted to
 * samples. Upstream validates far more than the fourcc-style containers do — a plausible sample
 * count/rate/format, a valid loop flag, not-all-zero coefficients, zero gain, and the
 * predictor/scale byte matching the first data byte — which is what makes endianness detectable.
 *
 * Upstream registers the big-endian header for `.dsp` and the little-endian one for `.adpcm`.
 * Only `.dsp` is registered here, but the big-endian parse is tried first and a little-endian
 * header is accepted as the fallback, so an LE rip that upstream would only reach through
 * `.adpcm` still parses. The strict header validation keeps the wrong endianness a parse miss.
 *
 * Out of scope: upstream's multi-channel/interleaved DSP variants (`.dsp` files with several
 * headers, `.adp`, `.mdsp`, …). Only a genuine mono `.dsp` is accepted.
 */
class DspReader(private val hatchet: Hatchet) : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        try {
            val stream = readStreamInfo(bytes)
            if (stream == null) {
                hatchet.w("DSP parse failed: no valid $HEADER_SIZE-byte DSP header.")
                null
            } else {
                listOf(dspTrack(identifier, SUBSONG_INDEX, stream))
            }
        } catch (e: IndexOutOfBoundsException) {
            hatchet.w("DSP parse failed: truncated header (${e.message}).")
            null
        } catch (e: IllegalArgumentException) {
            hatchet.w("DSP parse failed: ${e.message}.")
            null
        }

    /** Big-endian first (upstream's `.dsp` path), then little-endian (upstream's `.adpcm` path). */
    private fun readStreamInfo(bytes: ByteArray): DspStreamInfo? {
        if (bytes.size <= HEADER_SIZE) return null
        return readHeader(bytes, bigEndian = true) ?: readHeader(bytes, bigEndian = false)
    }

    private fun readHeader(bytes: ByteArray, bigEndian: Boolean): DspStreamInfo? {
        if (bytes.size < HEADER_SIZE) return null
        val reader = bytesAsReader(bytes)
        val header = DspHeader(
            sampleCount = reader.readU32(OFFSET_SAMPLE_COUNT, bigEndian),
            nibbleCount = reader.readU32(OFFSET_NIBBLE_COUNT, bigEndian),
            sampleRate = reader.readU32(OFFSET_SAMPLE_RATE, bigEndian),
            loopFlag = reader.readU16(OFFSET_LOOP_FLAG, bigEndian).toLong(),
            format = reader.readU16(OFFSET_FORMAT, bigEndian).toLong(),
            loopStartOffset = reader.readU32(OFFSET_LOOP_START, bigEndian),
            loopEndOffset = reader.readU32(OFFSET_LOOP_END, bigEndian),
            initialOffset = reader.readU32(OFFSET_INITIAL_OFFSET, bigEndian),
            coefsAllZero = (0 until COEF_COUNT).all {
                reader.readS16(OFFSET_COEFS + it * 2, bigEndian) == 0
            },
            gain = reader.readU16(OFFSET_GAIN, bigEndian).toLong(),
            initialPs = reader.readU16(OFFSET_INITIAL_PS, bigEndian).toLong(),
        )
        val firstDataByte = bytes[HEADER_SIZE].toLong() and BYTE_MASK
        return if (header.isValid(firstDataByte)) header.toStreamInfo() else null
    }

    private fun DspHeader.isValid(firstDataByte: Long): Boolean = listOf(
        sampleCount in 1..MAX_SAMPLE_COUNT,
        nibbleCount in 1..MAX_NIBBLE_COUNT,
        sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE,
        loopFlag <= MAX_LOOP_FLAG,
        format == DSP_FORMAT_ADPCM,
        initialOffset in INITIAL_OFFSETS || initialOffset == loopStartOffset,
        !coefsAllZero,
        gain == NO_GAIN,
        initialPs == firstDataByte,
    ).all { it }

    private fun DspHeader.toStreamInfo(): DspStreamInfo {
        val loopStart = dspNibblesToSamples(loopStartOffset.toInt())
        val loopEnd = minOf(dspNibblesToSamples(loopEndOffset.toInt()) + 1, sampleCount)
        return DspStreamInfo(
            sampleRate = sampleRate.toInt(),
            loopFlag = loopFlag != 0L,
            loopStartSample = loopStart,
            loopEndSample = loopEnd,
            numSamples = sampleCount,
        )
    }

    /** One decoded 0x60-byte DSPADPCM header; the fields upstream validates or uses. */
    private data class DspHeader(
        val sampleCount: Long,
        val nibbleCount: Long,
        val sampleRate: Long,
        val loopFlag: Long,
        val format: Long,
        val loopStartOffset: Long,
        val loopEndOffset: Long,
        val initialOffset: Long,
        val coefsAllZero: Boolean,
        val gain: Long,
        val initialPs: Long,
    )

    companion object {
        private const val HEADER_SIZE = 0x60
        private const val OFFSET_SAMPLE_COUNT = 0x00
        private const val OFFSET_NIBBLE_COUNT = 0x04
        private const val OFFSET_SAMPLE_RATE = 0x08
        private const val OFFSET_LOOP_FLAG = 0x0C
        private const val OFFSET_FORMAT = 0x0E
        private const val OFFSET_LOOP_START = 0x10
        private const val OFFSET_LOOP_END = 0x14
        private const val OFFSET_INITIAL_OFFSET = 0x18
        private const val OFFSET_COEFS = 0x1C
        private const val OFFSET_GAIN = 0x3C
        private const val OFFSET_INITIAL_PS = 0x3E
        private const val COEF_COUNT = 16

        private const val MAX_SAMPLE_COUNT = 0x10000000L
        private const val MAX_NIBBLE_COUNT = 0x20000000L
        private const val MIN_SAMPLE_RATE = 5_000L
        private const val MAX_SAMPLE_RATE = 48_000L
        private const val MAX_LOOP_FLAG = 1L
        private const val DSP_FORMAT_ADPCM = 0L
        private const val NO_GAIN = 0L
        private val INITIAL_OFFSETS = longArrayOf(0L, 2L)

        private const val BYTE_MASK = 0xFFL
        private const val SUBSONG_INDEX = 0
    }
}
