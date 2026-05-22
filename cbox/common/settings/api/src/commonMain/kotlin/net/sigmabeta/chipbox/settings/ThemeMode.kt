package net.sigmabeta.chipbox.settings

/**
 * User-selectable app theme. [SYSTEM] follows the OS light/dark setting and is the default;
 * [LIGHT] / [DARK] pin the respective Chipbox color scheme regardless of the system setting.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    companion object {
        val DEFAULT: ThemeMode = SYSTEM

        /** Parses a persisted [name] back into a [ThemeMode], falling back to [DEFAULT]. */
        fun fromStorageValue(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
