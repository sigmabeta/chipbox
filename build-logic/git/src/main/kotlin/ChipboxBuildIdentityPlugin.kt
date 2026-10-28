import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ProviderFactory
import java.io.File

/**
 * Exposes the `buildIdentity` accessors a module uses to stamp its build branch/commits.
 *
 * Applying the plugin also puts this build-logic jar on that module's build script classpath,
 * which is what lets the script call [BuildIdentity]; nothing else is configured.
 */
class ChipboxBuildIdentityPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create(
            "buildIdentity",
            BuildIdentity::class.java,
            project.providers,
            project.layout,
            project.rootDir,
        )
    }
}

/**
 * Build identity of the working copy, read from `.git` files rather than a `git` process (see
 * [GitMetadata]). Values are read when the build script asks for them — during configuration, but
 * with no process launch and no failure when `.git` is absent.
 *
 * A container or source-tarball build has no `.git`, so the checkout's identity can be supplied
 * out of band, matching the web app: the `chipbox.build.branch` / `chipbox.build.commit` Gradle
 * properties win over the `CHIPBOX_BUILD_BRANCH` / `CHIPBOX_BUILD_COMMIT` environment variables,
 * which win over `.git`. Both are read through providers, so they stay configuration-cache inputs.
 */
open class BuildIdentity(
    private val providers: ProviderFactory,
    private val layout: ProjectLayout,
    private val repoRoot: File,
) {
    /** The current branch, or the abbreviated commit when HEAD is detached. */
    fun branch(): String = override("chipbox.build.branch", "CHIPBOX_BUILD_BRANCH")
        ?: GitMetadata.branch(providers, layout, repoRoot)

    /**
     * The abbreviated commit of the working copy at [repoDir]. The out-of-band override names this
     * checkout, so it does not leak into a submodule's own commit.
     */
    fun shortCommit(repoDir: File = repoRoot): String {
        if (repoDir == repoRoot) {
            override("chipbox.build.commit", "CHIPBOX_BUILD_COMMIT")?.let { return it }
        }
        return GitMetadata.shortCommit(providers, layout, repoDir)
    }

    private fun override(property: String, environment: String): String? = providers.gradleProperty(property)
        .orElse(providers.environmentVariable(environment))
        .orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}
