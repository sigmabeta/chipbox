package net.sigmabeta.chipbox.debug

/**
 * Which image loader the UI uses — a debug-menu switch.
 *
 * [REAL] fetches cover art through Coil (network/disk); [FAKE] renders a deterministic generated
 * gradient instead (the same path previews/Paparazzi use) — handy for offline/screenshot-stable runs
 * and the default in UI tests.
 */
enum class ImageLoaderSource {
    REAL,
    FAKE,
    ;

    companion object {
        val DEFAULT: ImageLoaderSource = REAL

        fun fromStorageValue(value: String?): ImageLoaderSource =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
