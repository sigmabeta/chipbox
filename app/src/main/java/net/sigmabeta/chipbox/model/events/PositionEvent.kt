package net.sigmabeta.chipbox.model.events

class PositionEvent(val millisPlayed: Int) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String {
        return millisPlayed.toString()
    }

    companion object {
        val EVENT_TYPE = "position"
    }
}
