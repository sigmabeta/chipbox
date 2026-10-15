package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths

/**
 * A predicate over a file's corpus-relative path (`/`-separated), built from an optional glob.
 *
 * A `null` glob matches every path. A glob **without** `/` matches the file's **basename at any
 * depth** (`*.spc` is every `.spc` in the corpus, however nested). A glob **with** `/` matches the
 * whole corpus-relative path, where `**` crosses directories and `*` does not (a folder like
 * `Chrono Trigger` plus a trailing `**`). Because the walker's paths are always `/`-separated, the
 * glob is translated to the platform's separator before matching, so a pattern behaves identically
 * on every host.
 *
 * The pattern is validated eagerly by [requireValidGlob] so a malformed one fails at parse time,
 * before any corpus walk or native library load.
 */
internal fun corpusPathFilter(glob: String?): (String) -> Boolean {
    if (glob == null) return { true }
    val matcher = globMatcher(glob)
    return if ('/' in glob) {
        { relativePath -> matcher.matches(platformPath(relativePath)) }
    } else {
        { relativePath -> matcher.matches(Paths.get(relativePath.substringAfterLast('/'))) }
    }
}

/** Rejects a malformed [glob] up front, rather than mid-walk, naming the pattern in the message. */
internal fun requireValidGlob(glob: String) {
    runCatching { globMatcher(glob) }
        .onFailure { throw IllegalArgumentException("--filter must be a valid glob (was '$glob'): ${it.message}") }
}

private fun globMatcher(glob: String): PathMatcher =
    FileSystems.getDefault().getPathMatcher("glob:${glob.toPlatformSeparators()}")

private fun platformPath(relativePath: String): Path = Paths.get(relativePath.toPlatformSeparators())

/** Translates a `/`-separated glob to the host's separator, leaving it unchanged on `/`-hosts. */
private fun String.toPlatformSeparators(): String =
    if (File.separatorChar == '/') this else replace('/', File.separatorChar)
