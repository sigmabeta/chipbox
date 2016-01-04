package net.sigmabeta.chipbox.model.events

import net.sigmabeta.chipbox.model.objects.Track

class TrackEvent(val track: Track): PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String {
        return track.toString()
    }

    companion object {
        val EVENT_TYPE = "track"
    }
}
