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

// Force-bump npm transitives the Kotlin/JS toolchain drags in that Dependabot flags. yarn won't
// pick these up on its own because the parents declare older ranges, so each needs an explicit
// resolution; `kotlinUpgradeYarnLock` rewrites kotlin-js-store/yarn.lock to honor them.
//
//  - serialize-javascript / diff: RCE / DoS via mocha's 11.x test-runner (fixed in 7.x / 8.x). The
//    :unit-test-js workflow has been dropped from CI, so a mocha-major-bump regression here would
//    only surface in local jsTest invocations.
//  - webpack / webpack-dev-server / ws: the webpack bundler + dev-server stack. All same-major,
//    forward-compatible bumps to the patched releases — the buildHttp redirect allow-list bypass,
//    the dev-server cross-origin-over-HTTP bundle-load bypass, and the ws TypedArray-`reason`
//    uninitialized-memory read.
//  - uuid: the out-of-range-buffer-write fix only landed in 11.1.1 (no 8.x backport), so a major
//    bump is the only way to clear the alert. Its sole consumer here (sockjs, via
//    webpack-dev-server) does `require('uuid').v4()`; uuid 11 keeps a CommonJS `require` export
//    (package.json `exports.node.require` + `main` both point at dist/cjs), so sockjs is unaffected.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().apply {
        resolution("serialize-javascript", "^7.0.5")
        resolution("diff", "^8.0.4")
        resolution("webpack", "^5.102.0")
        resolution("webpack-dev-server", "^5.2.4")
        resolution("ws", "^8.21.0")
        resolution("uuid", "^11.1.1")
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
    // Mirror the ktlint exclusion for detekt: Compose-resource codegen produces source files
    // ([commonMain]Strings*.kt, Res.kt) that violate detekt's LongMethod/MaxLineLength/
    // FunctionNaming rules. The same `it.file.path.contains("/build/")` predicate the ktlint
    // exclusion above uses works on Detekt (SourceTask) and survives the absolute-path source
    // entries Compose's resourceGenerator registers — `exclude("**/build/**")` only matches
    // paths relative to the source roots and doesn't catch them.
    plugins.withId("io.gitlab.arturbosch.detekt") {
        tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
            exclude { it.file.path.contains("/build/") }
        }
    }
}

// One-shot dependency warm-up for CI's `setup` job. `resolveCiDependencies` resolves an app's
// runtime-classpath dependency GRAPH, downloading the component metadata + POMs into the Gradle
// module cache — exactly what the old `:apps:*:dependencies` report task did. It deliberately
// resolves `resolutionResult` (the graph) rather than `incoming.files` (the artifacts): forcing
// artifact selection on the Android release classpath fails with variant ambiguity, because
// picking the concrete jar/aar for each component is AGP-internal work the report task never did.
//
// The task is registered in each app project rather than the root because a configuration must be
// resolved by its OWNING project — resolving `:apps:android:releaseRuntimeClasspath` from a root
// task fails Gradle's "resolution without an exclusive lock" check. Both apps register the same
// task name, so a single unqualified `./gradlew resolveCiDependencies` runs both in ONE invocation
// — paying the dominant build-configuration cost once instead of the twice the two separate
// `:apps:*:dependencies` runs cost (they couldn't share an invocation: the `dependencies` report
// task takes a single `--configuration` and the two apps need different ones). The JVM/desktop deps
// aren't in the Android release classpath (Compose Desktop + Skia, Voyager, sqlite-bundled, metrox),
// so both classpaths must be warmed here.
mapOf(
    ":apps:android" to "releaseRuntimeClasspath",
    ":apps:jvm" to "runtimeClasspath",
).forEach { (path, configuration) ->
    project(path).tasks.register("resolveCiDependencies") {
        description = "Resolves this app's dependency graph to warm the Gradle module cache (CI warm-up)."
        group = "ci"
        doLast {
            this.project.configurations.getByName(configuration).incoming.resolutionResult.root
        }
    }
}

