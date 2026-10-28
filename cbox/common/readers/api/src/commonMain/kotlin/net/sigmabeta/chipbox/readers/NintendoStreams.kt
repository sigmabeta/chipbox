package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack

/**
 * Sample metadata shared by the Nintendo DSP-family streamed formats (BRSTM/BFSTM/BCSTM
 * containers and standalone `.dsp`). Only the fields the scan needs are kept: the linked
 * encoder/channel layout is irrelevant to a track's length and rate.
 *
 * [loopEndSample] is a sample index exclusive end plus one, exactly as vgmstream's metas leave it
 * (`loop_end_sample = num_samples` for the containers; `dsp_nibbles_to_samples(loop_end)+1`,
 * clamped to [numSamples], for a standalone DSP).
 */
internal data class DspStreamInfo(
    val sampleRate: Int,
    val loopFlag: Boolean,
    val loopStartSample: Long,
    val loopEndSample: Long,
    val numSamples: Long,
)

/**
 * Convert a DSP loop offset from nibbles to samples, mirroring `dsp_nibbles_to_samples` in
 * `coding/ngc_dsp_decoder.c`: whole 16-nibble frames hold 14 samples, and a partial frame's
 * nibbles map to `remainder - 2` samples (the two leading nibbles of a frame are its header).
 */
internal fun dspNibblesToSamples(nibbles: Int): Long {
    val wholeFrames = nibbles / NIBBLES_PER_FRAME
    val remainder = nibbles % NIBBLES_PER_FRAME
    return if (remainder > 0) {
        wholeFrames.toLong() * SAMPLES_PER_FRAME + remainder - PARTIAL_FRAME_BIAS
    } else {
        wholeFrames.toLong() * SAMPLES_PER_FRAME
    }
}

/**
 * One `RawTrack` for the stream at 0-based [subsongIndex], built through the shared
 * [streamedRawTrack] shape with the play length from [streamedLengthMs]. These containers carry no
 * stream name, so the title stays [TAG_UNKNOWN] and the scanner's filename fallback applies; none
 * of them implies a console, so the platform stays the [net.sigmabeta.chipbox.models.Platform.OTHER]
 * default.
 */
internal fun dspTrack(
    identifier: String,
    subsongIndex: Int,
    stream: DspStreamInfo,
): RawTrack = streamedRawTrack(
    identifier = identifier,
    lengthMs = streamedLengthMs(
        numSamples = stream.numSamples,
        loopFlag = stream.loopFlag,
        loopStartSample = stream.loopStartSample,
        loopEndSample = stream.loopEndSample,
        sampleRate = stream.sampleRate,
    ),
    subsongIndex = subsongIndex,
)

private const val NIBBLES_PER_FRAME = 16
private const val SAMPLES_PER_FRAME = 14L
private const val PARTIAL_FRAME_BIAS = 2L

private const val SHORT_MASK = 0xFFFF

/** Byte-reader helpers for the Nintendo headers, where one file switches endianness by BOM. */
internal fun ByteReader.readU16(offset: Int, bigEndian: Boolean): Int =
    (if (bigEndian) readShortBeAt(offset) else readShortLeAt(offset)).toInt() and SHORT_MASK

internal fun ByteReader.readS16(offset: Int, bigEndian: Boolean): Int =
    if (bigEndian) readShortBeAt(offset).toInt() else readShortLeAt(offset).toInt()

internal fun ByteReader.readU32(offset: Int, bigEndian: Boolean): Long =
    (if (bigEndian) readIntBeAt(offset) else readIntLeAt(offset)).toLong() and UINT32_MASK

internal fun ByteReader.readS32(offset: Int, bigEndian: Boolean): Int =
    if (bigEndian) readIntBeAt(offset) else readIntLeAt(offset)
