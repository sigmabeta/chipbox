plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    // Both linters are applied per-module by the SAGE base plugins (sage.kmp/android/jvm, via
    // configureDetekt()/configureKtlint()); declaring them here `apply false` puts their runtime on
    // the shared classpath so those by-id applications resolve. This replaced the old root
    // `subprojects {}` lint block (which Isolated Projects forbids).
    alias(libs.plugins.ktlint) apply false
}

// Use the system Node + Yarn (chipbox dev box has Node 22 and yarn 1.x installed) rather than
// letting Kotlin's JS plugin download its own. The plugin's download paths auto-add project-
// level `https://nodejs.org/dist` and `https://github.com/yarnpkg/yarn/releases/download`
// repos, which `RepositoriesMode.FAIL_ON_PROJECT_REPOS` in settings.gradle.kts rejects —
// `kotlinNodeJsSetup` / `kotlinYarnSetup` fail before any jsTest can run.
//
// Kotlin 2.3 deprecated the old NodeJsRootExtension.download — the new API is the EnvSpec types
// registered by NodeJsPlugin / YarnPlugin. Configure them on the root project only: Isolated
// Projects forbids the old `allprojects { }` traversal, and the actual node/yarn download is driven
// by the root `kotlin*Setup` tasks, so disabling it on the root EnvSpec is what keeps the toolchain
// from adding the `nodejs.org` / `yarnpkg` project repos that FAIL_ON_PROJECT_REPOS rejects. The
// whole JS toolchain is gated behind `-Psage.js`, so none of this configures in ordinary builds.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download.set(false)
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
    the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec>().download.set(false)
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().apply {
        resolution("serialize-javascript", "^7.0.5")
        resolution("diff", "^8.0.4")
        resolution("webpack", "^5.102.0")
        resolution("webpack-dev-server", "^5.2.4")
        resolution("ws", "^8.21.0")
        resolution("uuid", "^11.1.1")
    }
}

// ktlint + detekt are applied per-module by the SAGE base plugins (sage.kmp/android/jvm) via
// configureDetekt()/configureKtlint(); the `apply false` declarations above just put their runtime
// on the shared classpath. This replaced the old root `subprojects { }` lint block, which Isolated
// Projects forbids (a project configuring its siblings). Per-module apply keeps ktlint co-resident
// with each module's Kotlin plugin, which ktlint-gradle's KMP integration needs.

// The `resolveCiDependencies` CI dependency-cache warm-up is registered in each app's own build
// file (apps/android, apps/jvm) rather than here — Isolated Projects forbids the root reaching into
// `project(":apps:*").tasks`, and a configuration must be resolved by its owning project anyway.

