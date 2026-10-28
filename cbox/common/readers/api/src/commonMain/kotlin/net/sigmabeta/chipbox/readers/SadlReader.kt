package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * SADL streams from DS games with Procyon Studio's audio driver
 * (`cbox/native/vgmstream/Core/vgmstream/src/meta/sadl.c`; Professor Layton, Soma Bringer).
 *
 * One 32-bit little-endian header carries a loop flag, channels and a `flags` byte that encodes
 * both the sample rate (`flags & 0x06`) and the codec family (`flags & 0xF0`: IMA for 0x00/0x70,
 * NDS Procyon for 0xB0). Sample counts are derived from the data byte size, since there is no
 * sample-count field. Unknown rate/codec combinations are rejected. The format carries no stream
 * name, so the title falls back to [TAG_UNKNOWN] and the scanner derives it from the filename.
 *
 * Length policy (loops, fade, integer millisecond truncation) lives in [streamedLengthMs].
 */
class SadlReader(private val hatchet: Hatchet) : Reader() {

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        readStreamedTracks("SADL", identifier, hatchet) {
            val lengthMs = parseLengthMs(bytesAsReader(bytes))
            if (lengthMs == null) {
                hatchet.w("SADL parse failed: unrecognised or truncated header for $identifier.")
                null
            } else {
                listOf(streamedRawTrack(identifier = identifier, lengthMs = lengthMs))
            }
        }

    /** Returns the probe length in ms, or null when the header is not a SADL this reader accepts. */
    private fun parseLengthMs(buf: ByteReader): Long? {
        if (!buf.hasAsciiPrefix(MAGIC)) return null

        val loopFlag = buf.get(OFFSET_LOOP_FLAG).toInt() != 0
        val channels = buf.get(OFFSET_CHANNELS).toInt() and UINT8_MASK
        val flags = buf.get(OFFSET_FLAGS).toInt() and UINT8_MASK
        val dataSize = buf.readIntLeAt(OFFSET_DATA_SIZE).toLong() and UINT32_MASK
        val startOffset = buf.readIntLeAt(OFFSET_START_OFFSET).toLong() and UINT32_MASK
        val loopStartField = buf.readIntLeAt(OFFSET_LOOP_START).toLong() and UINT32_MASK

        val sampleRate = when (flags and RATE_BITS) {
            RATE_32728 -> 32_728
            RATE_16364_LAYTON, RATE_16364_LUMINOUS -> 16_364
            else -> 0
        }
        val codecFlags = flags and CODEC_BITS
        val isIma = codecFlags == IMA_LUMINOUS || codecFlags == IMA_LAYTON
        val isProcyon = codecFlags == PROCYON
        val knownCodec = isIma || isProcyon

        // Upstream subtracts the offsets as unsigned; here they stay signed so a nonsensical header
        // (data or loop start before the payload) is rejected instead of wrapping into a huge
        // sample count. Only a looping file needs a meaningful loop start.
        val dataBytes = dataSize - startOffset
        val loopStartBytes = loopStartField - startOffset
        val sizesOk = dataBytes > 0L && (!loopFlag || loopStartBytes >= 0L)
        val headerOk = channels >= 1 && sampleRate > 0
        val valid = headerOk && knownCodec && sizesOk
        if (!valid) return null

        val numSamples = if (isProcyon) {
            dataBytes / channels / PROCYON_BLOCK_BYTES * PROCYON_BLOCK_SAMPLES
        } else {
            dataBytes * IMA_SAMPLES_PER_BYTE / channels
        }
        val loopStart = if (isProcyon) {
            loopStartBytes / channels / PROCYON_BLOCK_BYTES * PROCYON_BLOCK_SAMPLES
        } else {
            loopStartBytes * IMA_SAMPLES_PER_BYTE / channels
        }

        return streamedLengthMs(
            numSamples = numSamples,
            loopFlag = loopFlag,
            loopStartSample = loopStart,
            loopEndSample = numSamples,
            sampleRate = sampleRate,
        )
    }

    private companion object {
        const val MAGIC = "sadl"

        const val OFFSET_LOOP_FLAG = 0x31
        const val OFFSET_CHANNELS = 0x32
        const val OFFSET_FLAGS = 0x33
        const val OFFSET_DATA_SIZE = 0x40
        const val OFFSET_START_OFFSET = 0x48
        const val OFFSET_LOOP_START = 0x54

        const val RATE_BITS = 0x06
        const val RATE_32728 = 4 // 0x04
        const val RATE_16364_LAYTON = 2 // 0x02
        const val RATE_16364_LUMINOUS = 0 // 0x00
        const val CODEC_BITS = 0xF0
        const val IMA_LUMINOUS = 0x00
        const val IMA_LAYTON = 0x70
        const val PROCYON = 0xB0

        const val IMA_SAMPLES_PER_BYTE = 2L // coding_IMA_mono: 2 samples per data byte per channel
        const val PROCYON_BLOCK_BYTES = 16L
        const val PROCYON_BLOCK_SAMPLES = 30L

        const val UINT8_MASK = 0xFF
    }
}
