import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `.git`-file resolution behind `chipbox.build.identity`: branch/commit stamps must come from
 * `HEAD`, loose refs, `packed-refs` and the `gitdir:`/`commondir` indirection — never from a `git`
 * process. These drive the pure resolver over real temp directories, so the linked-worktree and
 * detached-HEAD shapes are exercised without Gradle.
 */
class GitMetadataTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun `a missing dot-git directory reports unknown`() {
        val repo = tempDir()

        assertEquals(GitMetadata.UNKNOWN, branchOf(repo))
        assertEquals(GitMetadata.UNKNOWN, commitOf(repo))
    }

    @Test
    fun `a loose ref supplies the branch and its abbreviated commit`() {
        val repo = tempDir()
        write(repo, ".git/HEAD", "ref: refs/heads/feature/web-app\n")
        write(repo, ".git/refs/heads/feature/web-app", "$COMMIT\n")

        assertEquals("feature/web-app", branchOf(repo))
        assertEquals(COMMIT.take(7), commitOf(repo))
    }

    @Test
    fun `a packed ref is the fallback when the loose ref is absent`() {
        val repo = tempDir()
        write(repo, ".git/HEAD", "ref: refs/heads/main\n")
        write(
            repo,
            ".git/packed-refs",
            """
            # pack-refs with: peeled fully-peeled sorted
            $COMMIT refs/heads/main
            1111111111111111111111111111111111111111 refs/tags/v1
            """.trimIndent() + "\n",
        )

        assertEquals("main", branchOf(repo))
        assertEquals(COMMIT.take(7), commitOf(repo))
    }

    @Test
    fun `a detached head reports the abbreviated commit instead of the branch`() {
        val repo = tempDir()
        write(repo, ".git/HEAD", "$COMMIT\n")

        assertEquals(COMMIT.take(7), branchOf(repo))
        assertEquals(COMMIT.take(7), commitOf(repo))
    }

    @Test
    fun `a dot-git file follows gitdir and commondir for a linked worktree`() {
        val repo = tempDir()
        val worktreeGitDir = File(repo.parentFile, "${repo.name}-wt-gitdir")
        tempDirs += worktreeGitDir
        val commonGitDir = File(repo.parentFile, "${repo.name}-common-gitdir")
        tempDirs += commonGitDir

        write(repo, ".git", "gitdir: ../${worktreeGitDir.name}\n")
        write(worktreeGitDir, "HEAD", "ref: refs/heads/worktree-branch\n")
        write(worktreeGitDir, "commondir", "../${commonGitDir.name}\n")
        write(commonGitDir, "packed-refs", "$COMMIT refs/heads/worktree-branch\n")

        assertEquals("worktree-branch", branchOf(repo))
        assertEquals(COMMIT.take(7), commitOf(repo))
    }

    @Test
    fun `a relative gitdir is resolved against the dot-git file's directory`() {
        val repo = tempDir()
        write(repo, "actual-git/HEAD", "ref: refs/heads/relative\n")
        write(repo, "actual-git/refs/heads/relative", "$COMMIT\n")
        write(repo, ".git", "gitdir: actual-git\n")

        assertEquals("relative", branchOf(repo))
        assertEquals(COMMIT.take(7), commitOf(repo))
    }

    @Test
    fun `a malformed dot-git file degrades to unknown`() {
        val repo = tempDir()
        write(repo, ".git", "this is not a gitdir pointer\n")

        assertEquals(GitMetadata.UNKNOWN, branchOf(repo))
        assertEquals(GitMetadata.UNKNOWN, commitOf(repo))
    }

    // ---- helpers ----

    private fun branchOf(repo: File): String = GitMetadata.branch(repo, ::readFile)

    private fun commitOf(repo: File): String = GitMetadata.shortCommit(repo, ::readFile)

    /** Mirrors the production reader's "missing file means absent" contract, over the real FS. */
    private fun readFile(file: File): String? = runCatching {
        if (file.isFile) file.readText() else null
    }.getOrNull()

    private fun tempDir(): File =
        Files.createTempDirectory("git-metadata-test").toFile().also { tempDirs += it }

    private fun write(root: File, path: String, content: String) {
        File(root, path).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
    }

    private companion object {
        const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
    }
}
