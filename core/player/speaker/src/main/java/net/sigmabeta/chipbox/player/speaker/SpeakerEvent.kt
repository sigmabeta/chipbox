package net.sigmabeta.chipbox.player.speaker

sealed class SpeakerEvent {
    // What?!
    data class Error(
        val message: String
    ) : SpeakerEvent()

    // Speaker has begun playing audio from a different track.
    data class TrackChange(
        val trackId: Long
    ): SpeakerEvent()

    // Speaker wants audio, but has none; it waits.
    object Buffering : SpeakerEvent()

    // Speaker has audio and is playing it.
    object Playing : SpeakerEvent()
}