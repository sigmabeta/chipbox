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
    ): SpeakerEvent()

    /** The queue was empty when the speaker tried to pull. Indicates an underrun (or the
     *  initial pre-roll before playback starts). */
    object Buffering : SpeakerEvent()

    /** A buffer was successfully written to the sink. */
    object Playing : SpeakerEvent()
}
