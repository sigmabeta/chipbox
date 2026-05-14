package net.sigmabeta.chipbox.player.buffer

/**
 * One chunk of stereo 16-bit PCM travelling from a [ProducerBufferManager] to a
 * [ConsumerBufferManager]. The [data] array is owned by the buffer manager and recycled — the
 * speaker must finish reading from it before returning the buffer.
 *
 * @property trackId Track this audio belongs to. The speaker compares against the previous
 *           buffer's id to detect track-boundary crossings.
 * @property sampleRate Frame rate in Hz for [data]; may differ from the previous buffer's rate
 *           when the setlist crosses emulator boundaries.
 * @property frameIndex Index of [data]'s first frame within its track (0 at the start of a
 *           track, advancing by frames-generated per buffer). Combined with [sampleRate] this
 *           is a timestamp the speaker can use to report actual played position.
 * @property data Interleaved L/R 16-bit PCM samples. Length is fixed by the buffer pool.
 * @property fadeStartMs Position within the track where the speaker should begin the fade-out
 *           ramp. Computed by the producer from the track's declared length and fade window.
 * @property fadeLengthMs Duration of the fade-out ramp, in ms. After
 *           [fadeStartMs] + [fadeLengthMs] the speaker should output silence.
 */
data class AudioBuffer(
    val trackId: Long,
    val sampleRate: Int,
    val frameIndex: Long,
    val data: ShortArray,
    val fadeStartMs: Long,
    val fadeLengthMs: Long,
)
