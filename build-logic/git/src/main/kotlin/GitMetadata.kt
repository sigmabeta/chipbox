import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ProviderFactory
import java.io.File

/**
 * Build identity (`BUILD_BRANCH`, the benchmark's commit args) read from the working copy's `.git`
 * files — never by launching `git`.
 *
 * A `git` process on the configuration path costs a fork on every build: the configuration cache
 * re-runs a configuration-time `providers.exec` to validate its entry, so the process fires even
 * on a cache *hit*, and a tree without `.git` (Docker build context, source tarball) has nothing
 * to run against. These readers touch the files directly instead — `HEAD`, loose refs,
 * `packed-refs`, and the `gitdir:` / `commondir` indirection that linked worktrees and submodules
 * use — so a normal checkout reports real values with no process, and a tree with no `.git`
 * reports [UNKNOWN].
 *
 * Files are read through `providers.fileContents`, so their contents are configuration-cache
 * inputs: checking out another branch (or committing) invalidates the entry without needing a
 * process to compare against.
 */
object GitMetadata {
    /** Stamped when the value cannot be read from `.git`. */
    const val UNKNOWN: String = "unknown"

    private const val GITDIR_PREFIX = "gitdir: "

    /** `git rev-parse --short` abbreviates to at least seven characters. */
    private const val SHORT_COMMIT_LENGTH = 7

    private val commitSha = Regex("[0-9a-f]{40}")

    /**
     * The current branch name, or the abbreviated commit when HEAD is detached (the old
     * `rev-parse --abbrev-ref HEAD` reported the literal `HEAD` there, which is less useful).
     */
    fun branch(providers: ProviderFactory, layout: ProjectLayout, repoDir: File): String =
        branch(repoDir) { readFile(providers, layout, it) }

    /** The abbreviated commit of the working copy at [repoDir]. */
    fun shortCommit(providers: ProviderFactory, layout: ProjectLayout, repoDir: File): String =
        shortCommit(repoDir) { readFile(providers, layout, it) }

    /**
     * The pure resolver behind [branch], over a [repoDir] and a "read this file's text, or null
     * when absent/unreadable" function. Kept separate from Gradle's providers so the gitdir/ref
     * rules are testable without a build.
     */
    internal fun branch(repoDir: File, readFile: (File) -> String?): String {
        val gitDir = GitDir.of(repoDir, readFile) ?: return UNKNOWN
        val head = gitDir.read(readFile, gitDir.head) ?: return UNKNOWN
        val ref = head.removePrefix("ref: ")
        if (ref != head) {
            // `refs/heads/<name>` — the working copy is on a branch.
            return ref.removePrefix("refs/heads/")
        }
        return head.takeIf(commitSha::matches)?.take(SHORT_COMMIT_LENGTH) ?: UNKNOWN
    }

    /**
     * The pure resolver behind [shortCommit] — follows HEAD to its loose ref or `packed-refs`,
     * through the shared `commondir` when the checkout is a linked worktree or submodule.
     */
    internal fun shortCommit(repoDir: File, readFile: (File) -> String?): String {
        val gitDir = GitDir.of(repoDir, readFile) ?: return UNKNOWN
        val head = gitDir.read(readFile, gitDir.head) ?: return UNKNOWN
        val commit = if (head.startsWith("ref: ")) {
            gitDir.readRef(readFile, head.removePrefix("ref: ").trim())
        } else {
            head
        }
        return commit?.takeIf(commitSha::matches)?.take(SHORT_COMMIT_LENGTH) ?: UNKNOWN
    }

    /** Reads a small git metadata file (HEAD, a ref, packed-refs) through the configuration cache. */
    private fun readFile(providers: ProviderFactory, layout: ProjectLayout, file: File): String? {
        if (!file.isFile) return null
        // These values are informational, so an unreadable/racing checkout degrades to `unknown`
        // instead of failing configuration (the caller sees null).
        return runCatching {
            providers.fileContents(layout.file(providers.provider { file })).asText.orNull
        }.getOrNull()
    }

    /**
     * The git directory of a working copy. A normal checkout keeps it at `.git/`; a linked
     * worktree or submodule has a `.git` *file* pointing at the real directory, and refs may live
     * in a shared common directory (`commondir`) rather than the per-worktree one.
     */
    private class GitDir(private val gitDir: File, private val commonDir: File) {
        val head: File get() = File(gitDir, "HEAD")

        fun read(readFile: (File) -> String?, file: File): String? =
            readFile(file)?.trim()?.takeIf(String::isNotEmpty)

        fun readRef(readFile: (File) -> String?, ref: String): String? {
            if (!ref.startsWith("refs/")) return null
            // Worktrees share every ref through the common dir; only HEAD is per-worktree.
            readFile(File(commonDir, ref))?.trim()?.takeIf(commitSha::matches)?.let { return it }
            val packedRefs = readFile(File(commonDir, "packed-refs")) ?: return null
            return packedRefs.lineSequence()
                .map(String::trim)
                .firstOrNull { it.endsWith(" $ref") && !it.startsWith("#") && !it.startsWith("^") }
                ?.substringBefore(' ')
        }

        companion object {
            fun of(repoDir: File, readFile: (File) -> String?): GitDir? {
                val dotGit = File(repoDir, ".git")
                val gitDir = when {
                    dotGit.isDirectory -> dotGit
                    dotGit.isFile -> gitDirFromFile(dotGit, readFile) ?: return null
                    else -> return null
                }
                return GitDir(gitDir, commonDirOf(gitDir, readFile))
            }

            private fun gitDirFromFile(dotGit: File, readFile: (File) -> String?): File? {
                // Same degrade-to-unknown rule as readFile: a malformed `.git` is not fatal.
                val line = readFile(dotGit)
                    ?.lineSequence()
                    ?.firstOrNull { it.startsWith(GITDIR_PREFIX) }
                    ?: return null
                // A relative `gitdir:` is relative to the `.git` file's own directory.
                val path = File(line.removePrefix(GITDIR_PREFIX).trim())
                return if (path.isAbsolute) path else File(dotGit.parentFile, path.toString())
            }

            private fun commonDirOf(gitDir: File, readFile: (File) -> String?): File {
                val path = readFile(File(gitDir, "commondir"))?.trim()
                return if (path.isNullOrEmpty()) gitDir else File(gitDir, path)
            }
        }
    }
}
