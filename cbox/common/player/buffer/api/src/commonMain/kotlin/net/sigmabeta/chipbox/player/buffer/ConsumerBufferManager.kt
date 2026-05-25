package net.sigmabeta.chipbox.player.buffer

/**
 * Consumer-facing view of the buffer queue. The split into producer/consumer interfaces
 * exists so the [net.sigmabeta.chipbox.player.generator.Generator] and
 * [net.sigmabeta.chipbox.player.speaker.Speaker] can each only see the operations that make
 * sense from their side of the pipe.
 */
interface ConsumerBufferManager {
    /** Non-blocking poll. Returns null if the queue is empty — the speaker uses this to
     *  detect underruns and emit [net.sigmabeta.chipbox.player.speaker.SpeakerEvent.Buffering]
     *  before falling back to [waitForNextAudioBuffer]. */
    fun checkForNextAudioBuffer(): AudioBuffer?

    /** Suspend until a buffer is available. */
    suspend fun waitForNextAudioBuffer(): AudioBuffer

    /** Return a consumed buffer's backing array to the empty pool. The implementation zeroes
     *  the array before reuse so the producer always sees clean memory. */
    suspend fun recycleShortArray(data: ShortArray)

    /** Discard every queued [AudioBuffer], recycling each backing array. Used during seek so
     *  pre-seek audio doesn't continue playing into the post-seek position. */
    suspend fun drain()
}
