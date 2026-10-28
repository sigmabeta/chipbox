package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * SEB/GMS Game Arts streams (`cbox/native/vgmstream/Core/vgmstream/src/meta/seb.c`), dispatched
 * from both the `seb` and `gms` extensions.
 *
 * The header is 32-bit little-endian and magic-less: channels, sample rate, loop file offsets,
 * loop start sample, sample count and an **inverted** loop flag (the field is 0 for "loops").
 * Upstream derives the console from nothing — the mapping helper keeps these `OTHER`. The format
 * carries no stream name, so the title falls back to [TAG_UNKNOWN] and the scanner derives it from
 * the filename.
 *
 * Length policy (loops, fade, integer millisecond truncation) lives in [streamedLengthMs].
 */
class SebReader(private val hatchet: Hatchet) : Reader() {

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        readStreamedTracks("SEB", identifier, hatchet) {
            val lengthMs = parseLengthMs(bytesAsReader(bytes))
            if (lengthMs == null) {
                hatchet.w("SEB parse failed: unrecognised or truncated header for $identifier.")
                null
            } else {
                listOf(streamedRawTrack(identifier = identifier, lengthMs = lengthMs))
            }
        }

    /** Returns the probe length in ms, or null when the header is not a SEB this reader accepts. */
    private fun parseLengthMs(buf: ByteReader): Long? {
        val channels = buf.readIntLeAt(OFFSET_CHANNELS)
        val sampleRate = buf.readIntLeAt(OFFSET_SAMPLE_RATE)
        val loopStartOffset = buf.readIntLeAt(OFFSET_LOOP_START_OFFSET).toLong() and UINT32_MASK
        val loopStartSample = buf.readIntLeAt(OFFSET_LOOP_START_SAMPLE).toLong()
        val loopEndOffset = buf.readIntLeAt(OFFSET_LOOP_END_OFFSET).toLong() and UINT32_MASK
        val numSamples = buf.readIntLeAt(OFFSET_NUM_SAMPLES).toLong()
        // Upstream: loop_flag = (read_s32le(0x20) == 0) — zero means loop.
        val loopFlag = buf.readIntLeAt(OFFSET_LOOP_FIELD) == 0

        val formatOk = channels in 1..MAX_CHANNELS && sampleRate > 0
        val offsetsOk = loopStartOffset <= buf.size && loopEndOffset <= buf.size
        if (!formatOk || !offsetsOk) return null

        return streamedLengthMs(
            numSamples = numSamples,
            loopFlag = loopFlag,
            loopStartSample = loopStartSample,
            loopEndSample = numSamples,
            sampleRate = sampleRate,
        )
    }

    private companion object {
        const val OFFSET_CHANNELS = 0x00
        const val OFFSET_SAMPLE_RATE = 0x04
        const val OFFSET_LOOP_START_OFFSET = 0x10
        const val OFFSET_LOOP_START_SAMPLE = 0x14
        const val OFFSET_LOOP_END_OFFSET = 0x18
        const val OFFSET_NUM_SAMPLES = 0x1C
        const val OFFSET_LOOP_FIELD = 0x20

        const val MAX_CHANNELS = 2
    }
}
