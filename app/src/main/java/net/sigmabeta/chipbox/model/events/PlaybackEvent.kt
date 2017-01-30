package net.sigmabeta.chipbox.model.events

abstract class PlaybackEvent {
    abstract fun getType(): String

    abstract fun getDataAsString(): String?

    override fun toString(): String {
        return "Event: ${getType()} Data: ${getDataAsString()}"
    }
}
