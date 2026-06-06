package net.sigmabeta.chipbox.player.buffer

/**
 * Producer-facing view of the buffer queue between [net.sigmabeta.chipbox.player.generator.Generator]
 * and [net.sigmabeta.chipbox.player.speaker.Speaker]. Implementations back this with a bounded
 * channel so a fast producer naturally suspends when the consumer falls behind.
 */
interface ProducerBufferManager {
    /** Allocate (or reallocate) the empty/full pools sized for the given rate. Must be called
     *  before any [getNextEmptyBuffer] call for a track at this rate. */
    suspend fun setSampleRate(sampleRate: Int)

    /** Borrow a zero-initialized [ShortArray] from the empty pool. Suspends if all arrays are
     *  currently in flight on the consumer side. */
    suspend fun getNextEmptyBuffer(): ShortArray

    /** Push a filled buffer to the consumer. Suspends when the queue is full — this is the
     *  pipeline's flow-control mechanism. */
    suspend fun sendAudioBuffer(audioBuffer: AudioBuffer)

    /** Discard the current pool and forget the current rate, so the next [setSampleRate] always
     *  rebuilds a full empty pool — even for the same rate. The manager is a process singleton, so
     *  when the app is killed but the process survives, its pool can be left drained at the last
     *  rate; [setSampleRate] would then no-op and [getNextEmptyBuffer] would block forever. A
     *  generator starting a fresh produce loop calls this so a cold relaunch starts clean. */
    suspend fun reset()
}
