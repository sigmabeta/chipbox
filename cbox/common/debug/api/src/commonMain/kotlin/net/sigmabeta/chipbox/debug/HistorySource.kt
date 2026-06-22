package net.sigmabeta.chipbox.debug

/**
 * Which `PlaybackHistoryRepository` the app injects — a debug-menu switch.
 *
 * [REAL] is the Room-backed store; [FAKE] is the in-memory one (empty on launch, lost on exit) —
 * handy for exercising the history-driven UI without touching the database. Read once when the DI
 * graph is built, so a change applies on the next app launch.
 */
enum class HistorySource {
    REAL,
    FAKE,
    ;

    companion object {
        val DEFAULT: HistorySource = REAL

        fun fromStorageValue(value: String?): HistorySource =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
