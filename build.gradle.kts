plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Use the system Node + Yarn (chipbox dev box has Node 22 and yarn 1.x installed) rather than
// letting Kotlin's JS plugin download its own. The plugin's download paths auto-add project-
// level `https://nodejs.org/dist` and `https://github.com/yarnpkg/yarn/releases/download`
// repos, which `RepositoriesMode.FAIL_ON_PROJECT_REPOS` in settings.gradle.kts rejects —
// `kotlinNodeJsSetup` / `kotlinYarnSetup` fail before any jsTest can run.
//
// Kotlin 2.3 deprecated the old NodeJsRootExtension.download — the new API is the EnvSpec
// types registered per-project by NodeJsPlugin / YarnPlugin (each KMP module with a js()
// target applies them locally, so we configure on every subproject, not just root).
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download.set(false)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec>().download.set(false)
    }
}

// ktlint via the Gradle plugin, replacing the old ktlint-check.sh / ktlint-fix.sh that
// downloaded the ktlint binary and ran it outside Gradle. Applied to every chipbox module;
// the vendored sage/ submodule is a separate included build and keeps its own lint config.
// `ktlintCheck` (wired into `check`) lints, `ktlintFormat` auto-fixes. The engine is pinned to
// the version the old script downloaded so the active ruleset doesn't change.
val ktlintToolVersion = libs.versions.ktlintTool.get()
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintToolVersion)
    }
    // KSP/Room and Compose Multiplatform register their generated sources (e.g.
    // ChipboxDatabase_Impl.kt, the Compose `Res` accessors) into the Kotlin source sets, so
    // ktlint-gradle would lint them. The old ktlint-check.sh excluded `**/build/**`; keep that
    // exclusion so we only lint hand-written code.
    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
        exclude { it.file.path.contains("/build/") }
    }
}
