package net.sigmabeta.chipbox.debug

/**
 * Which [Repository][net.sigmabeta.chipbox.repository.Repository] implementation serves data —
 * a debug-menu switch.
 *
 * [REAL] is the platform's production repository (the on-device database, or the remote API on
 * web); [MEMORY] is an empty in-memory repository; [RANDOM] is an in-memory repository pre-filled
 * with deterministic fake data. Used by a switchable repository that forwards to the selected impl.
 */
enum class RepositorySource {
    REAL,
    MEMORY,
    RANDOM,
    ;

    companion object {
        val DEFAULT: RepositorySource = REAL

        /** Parses a persisted [value] back into a [RepositorySource], falling back to [DEFAULT]. */
        fun fromStorageValue(value: String?): RepositorySource =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
