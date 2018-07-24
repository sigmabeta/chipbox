package net.sigmabeta.chipbox.model.events

class TrackEvent(val trackId: String?) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String? {
        return trackId
    }

    companion object {
        val EVENT_TYPE = "track"
    }
}
