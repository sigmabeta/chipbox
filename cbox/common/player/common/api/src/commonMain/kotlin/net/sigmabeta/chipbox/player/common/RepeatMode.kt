package net.sigmabeta.chipbox.player.common

/**
 * How the director should behave when the current track finishes playing. Carried on the
 * [Session] (like [Session.shuffled]) so the playback state machine can consult it on
 * auto-advance, and so the now-playing UI can render the repeat button from the same source.
 */
enum class RepeatMode {
    /** No repeat: advance through the setlist and stop once the last track ends. */
    OFF,

    /** Repeat the whole setlist: wrap back to the first track after the last one ends. */
    ALL,

    /** Repeat the current track: restart it from the beginning each time it ends. */
    ONE;

    /** Next mode in the round-robin the repeat button cycles through: OFF → ALL → ONE → OFF. */
    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}
