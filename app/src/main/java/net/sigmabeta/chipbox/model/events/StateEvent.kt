package net.sigmabeta.chipbox.model.events

class StateEvent(val state: Int) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String {
        return state.toString()
    }

    companion object {
        val EVENT_TYPE = "state"
    }
}
