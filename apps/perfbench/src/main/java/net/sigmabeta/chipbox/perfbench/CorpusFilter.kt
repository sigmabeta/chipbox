package net.sigmabeta.chipbox.perfbench

import java.nio.file.FileSystems
import java.nio.file.Paths

/**
 * A predicate over a file's corpus-relative path (`/`-separated), built from an optional glob.
 *
 * A `null` glob matches every path. Otherwise the glob is a `java.nio.file` glob: `*` does not cross
 * a directory separator and `**` does. The pattern is validated eagerly by [requireValidGlob] so a
 * malformed one fails at parse time, before any corpus walk or native library load.
 */
internal fun corpusPathFilter(glob: String?): (String) -> Boolean {
    if (glob == null) return { true }
    val matcher = FileSystems.getDefault().getPathMatcher("glob:$glob")
    return { relativePath -> matcher.matches(Paths.get(relativePath)) }
}

/** Rejects a malformed [glob] up front, rather than mid-walk, naming the pattern in the message. */
internal fun requireValidGlob(glob: String) {
    runCatching { FileSystems.getDefault().getPathMatcher("glob:$glob") }
        .onFailure { throw IllegalArgumentException("--filter must be a valid glob (was '$glob'): ${it.message}") }
}
