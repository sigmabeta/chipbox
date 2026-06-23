package net.sigmabeta.chipbox.debug

/**
 * Which `PlaylistsRepository` the app injects — a debug-menu switch.
 *
 * [REAL] is the Room-backed store; [FAKE] is the in-memory one (empty on launch, lost on exit) —
 * handy for exercising the playlists UI/playback without touching the database. Read once when the
 * DI graph is built, so a change applies on the next app launch.
 */
enum class PlaylistsSource {
    REAL,
    FAKE,
    ;

    companion object {
        val DEFAULT: PlaylistsSource = REAL

        fun fromStorageValue(value: String?): PlaylistsSource =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
