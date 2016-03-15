package net.sigmabeta.chipbox.model.events

import net.sigmabeta.chipbox.model.objects.Game

class GameEvent(val game: Game?) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String {
        return game?.toString() ?: "Unknown Game"
    }

    companion object {
        val EVENT_TYPE = "game"
    }
}
