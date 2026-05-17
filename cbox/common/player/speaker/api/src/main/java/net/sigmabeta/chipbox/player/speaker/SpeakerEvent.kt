package net.sigmabeta.chipbox.player.speaker

/**
 * Things the [Speaker] reports back as it consumes the audio queue. Observed by the
 * [net.sigmabeta.chipbox.player.director.Director] to drive playback-state transitions.
 */
sealed class SpeakerEvent {
    /** The sink reported a fatal write error. */
    data class Error(
        val message: String
    ) : SpeakerEvent()

    /** A buffer arrived whose `trackId` differs from the previous one — the speaker has
     *  crossed a track boundary. The director uses this to update displayed metadata. */
    data class TrackChange(
        val trackId: Long
    ) : SpeakerEvent()

    /** The queue was empty when the speaker tried to pull. Indicates an underrun (or the
     *  initial pre-roll before playback starts). [positionMs] is the speaker's play head at
     *  the moment of the underrun. */
    data class Buffering(val positionMs: Long) : SpeakerEvent()

    /** A buffer was successfully written to the sink. [positionMs] is sampled right before the
     *  write, so consecutive Playing emissions carry monotonically advancing positions — this
     *  also keeps `distinctUntilChanged()` from collapsing them, so playback-state observers
     *  see live position updates instead of one stuck value. */
    data class Playing(val positionMs: Long) : SpeakerEvent()
}
