plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    application
}

application {
    mainClass.set("net.sigmabeta.chipbox.server.MainKt")
    applicationName = "chipbox-server"
}

// Headless HTTP server that serves the apps/js bundle + JSON library API. Reuses the entire
// scan + DB stack apps/cli already runs (DatabaseRepository + RealScanner + LocalFileContentSource)
// — see ChipboxLibrary.kt for the hand-wired construction pattern this mirrors. No emulator
// playback on the server (that's a future WASM project for the browser); the only native lib it
// needs is vgmstream for the scanner's subsong probe.

// libvgmstream.so: scanner.real → VgmstreamProbe.probe → System.loadLibrary("vgmstream"). Same
// pattern apps/cli uses (apps/cli/build.gradle.kts comments explain in detail). One native lib,
// no per-format emulator builds — pure Kotlin readers handle everything else during a scan.
val jvmNativeLibsDir = project(":apps:jvm").layout.projectDirectory.dir("libs").asFile
val buildVgmstreamLib = ":apps:jvm:nativeEmulatorVgmstream"

// Bundle copy is opt-in. By default `:apps:server:run` skips webpack entirely — Ktor's
// `staticResources("/", "static")` just 404s on `/`, which is fine when you're running the
// webpack-dev-server separately on :8081 (RemoteRepository auto-routes API calls to :8080).
// Opt in with `-Pchipbox.server.bundleJs` to have the server host the bundle itself; pair with
// `-Pchipbox.server.productionBundle` for the minified bundle (deployment).
//
// `installDist` / `distZip` / `distTar` always force the production bundle below — those are
// for shipping, where you can't skip it.
// Resolve both `-P` flags at config time. `dependsOn` is also config-time, so we omit the
// webpack-task dependency entirely when `bundleJs` is false — that's what keeps the default
// `:apps:server:run` webpack-free. (An `onlyIf` on the Copy task alone wouldn't help: its
// upstream `dependsOn` still fires before Gradle checks `onlyIf`.)
val bundleJsEnabled = providers.gradleProperty("chipbox.server.bundleJs")
    .map { it.toBoolean() }.getOrElse(false)
val productionBundle = providers.gradleProperty("chipbox.server.productionBundle")
    .map { it.toBoolean() }.getOrElse(false)
val bundleDir = if (productionBundle) "dist/js/productionExecutable" else "dist/js/developmentExecutable"
val bundleTask = if (productionBundle) {
    ":apps:js:jsBrowserDistribution"
} else {
    ":apps:js:jsBrowserDevelopmentExecutableDistribution"
}

val copyJsBundle = tasks.register<Copy>("copyJsBundle") {
    description = "Copies the apps/js webpack bundle into apps/server's static resources " +
        "(opt-in via -Pchipbox.server.bundleJs)."
    group = "build"
    if (bundleJsEnabled) {
        dependsOn(bundleTask)
        from(project(":apps:js").layout.buildDirectory.dir(bundleDir))
        into(layout.buildDirectory.dir("generated/static/static"))
    }
    // Skip-condition via a Provider rather than a captured Boolean — config cache rejects
    // closures that capture script-local primitives, but Providers are explicitly cache-safe.
    val enabled = providers.gradleProperty("chipbox.server.bundleJs")
        .map { it.toBoolean() }.orElse(false)
    onlyIf("chipbox.server.bundleJs is true") { enabled.get() }
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated/static"))
    }
}
tasks.named("processResources") {
    dependsOn(copyJsBundle)
    // installProdBundle (declared below) writes into the same `generated/static/static`
    // directory. Gradle 9 wants the relationship declared explicitly even though
    // installProdBundle is only in the graph for installDist/distZip/distTar. mustRunAfter
    // gives the ordering without forcing installProdBundle into every `:apps:server:run`.
    mustRunAfter(installProdBundle)
}

tasks.named<JavaExec>("run") {
    dependsOn(buildVgmstreamLib)
    systemProperty("java.library.path", jvmNativeLibsDir.absolutePath)
}

// Bundle libvgmstream.so into `lib/native/` in the installed distribution + splice
// `-Djava.library.path=$APP_HOME/lib/native` into the launch scripts. Same shape as apps/cli.
distributions {
    named("main") {
        contents {
            from(jvmNativeLibsDir) {
                include("libvgmstream.so")
                into("lib/native")
            }
        }
    }
}
// Always-on production bundle copy used by installDist/distZip/distTar (separate from the
// opt-in `copyJsBundle` above so default `:apps:server:run` stays webpack-free).
val installProdBundle = tasks.register<Copy>("installProductionJsBundle") {
    description = "Copies the production apps/js bundle into the server's static resources " +
        "(used by installDist/distZip/distTar)."
    group = "build"
    dependsOn(":apps:js:jsBrowserDistribution")
    from(project(":apps:js").layout.buildDirectory.dir("dist/js/productionExecutable"))
    into(layout.buildDirectory.dir("generated/static/static"))
}

listOf("installDist", "distZip", "distTar").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(buildVgmstreamLib)
        // Shippable artifacts must include the production JS bundle regardless of -P flags;
        // wiring an explicit Copy that doesn't respect `onlyIf` keeps that contract.
        dependsOn(installProdBundle)
    }
}
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        unixScript.writeText(
            unixScript.readText().replace(
                "exec \"\$JAVACMD\" \"\$@\"",
                "exec \"\$JAVACMD\" \"-Djava.library.path=\$APP_HOME/lib/native\" \"\$@\"",
            ),
        )
        windowsScript.writeText(
            windowsScript.readText().replace(
                "-classpath \"%CLASSPATH%\"",
                "-D\"java.library.path=%APP_HOME%\\lib\\native\" -classpath \"%CLASSPATH%\"",
            ),
        )
    }
}

dependencies {
    // The chipbox scan + library stack — same modules apps/cli pulls in (minus the cover-art /
    // organizer / Mordant CLI bits this server doesn't need).
    implementation(projects.cbox.common.scanner.real)
    implementation(projects.cbox.common.scanner.api)
    implementation(projects.cbox.common.player.emulators.vgmstream.real)
    implementation(projects.cbox.common.repository.real)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.database.real)
    implementation(projects.cbox.common.contentsource.api)
    implementation(projects.cbox.common.contentsource.file.real)
    implementation(projects.cbox.common.readers.api)
    implementation(projects.cbox.common.models.api)
    implementation(projects.cbox.common.strings.real)
    implementation(libs.sage.common.ui.strings)
    implementation(libs.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sage.common.logging)

    // Ktor server + content negotiation + plugins for status pages, CORS, partial content
    // (Range requests), call logging.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // SLF4J backend so Ktor's logging output appears on stdout.
    implementation(libs.logback.classic)

    // Metro DI — ServerChipboxGraph is the runtime DI root, same shape as JvmChipboxGraph in
    // apps/jvm. AppScope lives in sage/common/di.
    implementation(libs.sage.common.di)
    // okio.FileSystem — RealScanner's path machinery uses okio (multiplatform). Same dep as
    // apps/jvm and apps/cli; pull it in directly so JvmFileSystem-style provides can return
    // FileSystem.SYSTEM if needed by anything we wire later.
    implementation(libs.okio)
}
