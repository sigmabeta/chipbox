package net.sigmabeta.chipbox.contentsource

/**
 * Two configured library roots that name the same folder, or one that contains the other.
 * Discovered in configuration order, so [first] is the root configured earlier.
 */
data class LibraryLocationOverlap(
    val first: String,
    val second: String,
)

/**
 * The disjointness rule for library roots.
 *
 * A [LibrarySource] walks every configured root independently and keys each discovered folder by
 * its path. When one root contains another (or the same root is configured twice) the shared
 * folders are emitted more than once in a single scan, and the parallel readers race to insert the
 * same `game.folder_key` — one loses to the unique index and the whole scan aborts. Tolerating the
 * overlap would also duplicate every game that lives under the nested root, so the configuration
 * is refused instead.
 *
 * The comparison is segment-wise, not a string prefix: `/music/SNES` contains `/music/SNES/Album`
 * but *not* `/music/SNES2`. Trailing/repeated separators and `/` vs `\` are ignored. Callers that
 * may be handed non-normalized paths (`.`, `..`, symlinks, case-folded aliases) should
 * canonicalize through their okio `FileSystem` first, exactly as the local-file source does.
 */
object LibraryLocations {

    /**
     * The first overlapping pair in [identifiers], in configuration order, or null when every
     * root is disjoint. [comparisonKey] maps a configured identifier to the value compared —
     * a filesystem source passes a canonicalizer so `..`, trailing separators and symlinks are
     * resolved; the reported pair still holds the identifiers as configured, which is what the
     * admin needs to see.
     */
    fun firstOverlap(
        identifiers: List<String>,
        comparisonKey: (String) -> String = { it },
    ): LibraryLocationOverlap? {
        val keys = identifiers.map(comparisonKey)
        for (first in identifiers.indices) {
            for (second in first + 1 until identifiers.size) {
                if (overlaps(keys[first], keys[second])) {
                    return LibraryLocationOverlap(identifiers[first], identifiers[second])
                }
            }
        }
        return null
    }

    /** True when [first] is [second], contains it, or is contained by it. */
    fun overlaps(first: String, second: String): Boolean {
        val firstSegments = segmentsOf(first)
        val secondSegments = segmentsOf(second)
        val shared = minOf(firstSegments.size, secondSegments.size)
        return firstSegments.subList(0, shared) == secondSegments.subList(0, shared)
    }

    // Absolute paths only (the identifiers [LibrarySource] persists); the leading separator
    // produces an empty first segment, which is dropped so it can't hide a mismatch.
    private fun segmentsOf(path: String): List<String> =
        path.split('/', '\\').filter { it.isNotEmpty() }
}
