package net.sigmabeta.chipbox.perfbench

import java.io.File

/**
 * Locates the repository root by walking up from [start] until it finds a `.git` entry. Used so the
 * CLI can stamp the Chipbox and sage commits regardless of the working directory Gradle launches it
 * from.
 */
internal fun resolveRepoRoot(start: File = File(System.getProperty("user.dir"))): File? {
    var directory: File? = start.absoluteFile
    while (directory != null) {
        if (File(directory, ".git").exists()) return directory
        directory = directory.parentFile
    }
    return null
}

/** The short HEAD commit of the git repository at [directory], or `"unknown"` if it can't be read. */
internal fun gitShortCommit(directory: File): String = runCatching {
    val process = ProcessBuilder("git", "-C", directory.absolutePath, "rev-parse", "--short", "HEAD")
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    process.waitFor()
    output
}.getOrDefault("").ifEmpty { "unknown" }

/** The Chipbox root commit and the sage submodule commit, as recorded in a fingerprint. */
internal fun projectCommits(repoRoot: File): Pair<String, String> =
    gitShortCommit(repoRoot) to gitShortCommit(File(repoRoot, "sage"))
