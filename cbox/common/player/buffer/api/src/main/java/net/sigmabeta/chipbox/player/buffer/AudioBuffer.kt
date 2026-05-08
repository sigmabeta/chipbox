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
 * @property data Interleaved L/R 16-bit PCM samples. Length is fixed by the buffer pool.
 */
data class AudioBuffer(
    val trackId: Long,
    val sampleRate: Int,
    val data: ShortArray
)
