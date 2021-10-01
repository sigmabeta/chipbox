package net.sigmabeta.chipbox.player.speaker

sealed class SpeakerEvent {
    data class Error(
        val message: String
    ) : SpeakerEvent()

    data class TrackChange(
        val trackId: Long
    ): SpeakerEvent()

    object Buffering : SpeakerEvent()

    object Playing : SpeakerEvent()
}