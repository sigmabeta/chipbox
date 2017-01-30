package net.sigmabeta.chipbox.model.events

class GameEvent(val gameId: String?) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String? {
        return gameId
    }

    companion object {
        val EVENT_TYPE = "game"
    }
}
