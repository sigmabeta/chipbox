plugins {
    alias(libs.plugins.sage.jvm)
    // Host emulator native build (shared cacheable CMake task; provides `chipboxHostNativeLibs`).
    alias(chipbox.plugins.native.host)
    alias(libs.plugins.metro)
    // Compose Multiplatform desktop. Two plugins are needed: the Kotlin Compose compiler
    // (shared with the Android UI) handles @Composable codegen, and the JetBrains Compose
    // plugin provides the `compose.desktop.currentOs` dependency notation that resolves the
    // per-OS Skia native artifact. Both must be applied here; only the compiler plugin will
    // travel through the future sage.compose.kmp convention for shared UI modules.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Host emulator native libs are built by the chipbox.native.host plugin (the shared cacheable
// build in build-logic) into build/jvm-native/libs/ via the `chipboxHostNativeLibs` aggregate
// task. The compose.desktop `run` task + the jpackage app image wire to that directory + task below.
//
// Debug vs release: `AppInfo.isDebug` (JvmModules.kt) reads `-Dchipbox.debug` and defaults to release
// (false), which drives the window title ("Chipbox" vs "Chipbox Debug"), the window icon, and the
// swapped/plain color scheme. The dev `run` task opts into debug (`-Dchipbox.debug=true`) and uses the
// purple `splash_debug.png`; the packaged installers stay release and bundle the yellow `splash.png`.
// (`-splash:` is a launcher arg shown before main() runs, so the splash is picked at build time, not
// from the runtime flag.)
val splashDebugFile: File = layout.projectDirectory.dir("src/main/splash").file("splash_debug.png").asFile
val splashReleaseFile: File = layout.projectDirectory.dir("src/main/splash").file("splash.png").asFile
val nativeLibsDirFile: File = layout.buildDirectory.dir("jvm-native/libs").get().asFile
val nativeLibsTask = tasks.named("chipboxHostNativeLibs")

// --- Packaging: compose.desktop.application (jpackage) --------------------------------------------
// Migrated off the Gradle `application` plugin (native-installers phase): that plugin and
// compose.desktop.application both register a `run` task and can't coexist, so Compose now owns
// packaging end to end. It provides `run` (still a JavaExec → `--args="gui"` works), a JRE-bundled
// app image (`createDistributable`), and the native installers (`packageDeb` / `packageRpm`; Windows
// .msi + macOS .dmg follow once their native builds exist).
//
// The emulator natives are staged into the per-OS appResources layout Compose expects: it flattens
// `<root>/common` + `<root>/<os.id>` into the app's `resources` dir, which jpackage materializes at
// `$APPDIR/resources`. java.library.path is pointed there for the packaged launcher so the emulators'
// `System.loadLibrary("usf")` resolve without touching the (Android-shared) loader code.
val hostOsResourceDir: String = when {
    org.gradle.internal.os.OperatingSystem.current().isWindows -> "windows"
    org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "macos"
    else -> "linux"
}
// jpackage requires a strict numeric version (X.Y.Z); CI passes the git tag sanitized to its numeric
// prefix. Local builds (and non-tag CI) fall back to a static placeholder.
val jpackageVersion: String = (project.findProperty("chipbox.jvm.packageVersion") as String?) ?: "3.0.0"
val appResourcesRoot: Provider<Directory> = layout.buildDirectory.dir("jpackage-resources")
// Windows .msi must be packaged on windows-latest (jpackage can't cross-compile), but there's no
// native Windows-host build of the cores. jpackage only *assembles* — it doesn't compile natives — so
// the Windows job supplies the .dll cross-compiled on a Linux job via this property and we skip the
// native build entirely. Unset (the default) → build the host natives normally (Linux .deb/.rpm, run).
val prebuiltNativeDir: String? = project.findProperty("chipbox.jvm.prebuiltNativeDir") as String?
val stageAppResources by tasks.registering(Copy::class) {
    if (prebuiltNativeDir != null) {
        from(prebuiltNativeDir) {
            include("*.dll", "*.so", "*.dylib")
            into(hostOsResourceDir)
        }
    } else {
        dependsOn(nativeLibsTask)
        from(nativeLibsDirFile) { into(hostOsResourceDir) } // .so/.dll/.dylib → $APPDIR/resources
    }
    from(splashReleaseFile) {
        // Release splash → $APPDIR/resources/splash.png (for the -splash launcher arg). Packaged
        // installers are release, so they get the yellow splash; `run` uses the debug one below.
        into("common")
        rename { "splash.png" }
    }
    into(appResourcesRoot)
}

compose.desktop {
    application {
        mainClass = "net.sigmabeta.chipbox.jvm.MainKt"
        // jpackage lives in a full JDK; a dev machine's Gradle JDK may be a stripped JBR without it.
        // Override locally with -Pchipbox.jvm.jpackageJdk=/path/to/jdk21. CI's Temurin 21 has jpackage,
        // so it leaves this unset and Compose uses the build JDK.
        (project.findProperty("chipbox.jvm.jpackageJdk") as String?)?.let { javaHome = it }
        // Passed to the packaged jpackage launcher (--java-options); jpackage substitutes $APPDIR at
        // launch. The plain `run` task below overrides these with absolute paths (no $APPDIR there).
        jvmArgs += listOf(
            "-Djava.library.path=\$APPDIR/resources",
            "-splash:\$APPDIR/resources/splash.png",
        )
        nativeDistributions {
            // Each format only builds on its compatible OS (Compose disables the rest), so declaring
            // all four is safe: ubuntu builds Deb/Rpm, windows-latest builds Msi, macos-latest Dmg.
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
            )
            packageName = "chipbox"
            packageVersion = jpackageVersion
            // Bundle the full JDK runtime: the app pulls in modules (java.sql via sqlite, java.naming,
            // java.desktop via Swing/AWT, the unix-socket transport) that module inference can miss
            // and would surface only as runtime ClassNotFound. Trim later via suggestRuntimeModules.
            includeAllModules = true
            appResourcesRootDir.set(appResourcesRoot)
            windows {
                // Stable UUID so future versions upgrade the install in place rather than stacking
                // side-by-side. Start-menu shortcut + group; let the user pick the install dir.
                upgradeUuid = "44f80530-fd71-4dd3-9d94-00c76e65a537"
                menuGroup = "Chipbox"
                menu = true
                shortcut = true
                // Without an explicit icon jpackage stamps the launcher with the Compose/Kotlin
                // default. Multi-resolution .ico generated from the app launcher icon (icons/).
                iconFile.set(project.file("icons/chipbox.ico"))
            }
            linux {
                iconFile.set(project.file("icons/chipbox.png"))
            }
            macOS {
                // Reverse-DNS bundle identifier for the .app. The .dmg ships UNSIGNED for now
                // (Gatekeeper will warn: right-click → Open, or `xattr -dr com.apple.quarantine`);
                // Developer-ID signing + notarization is a TODO.
                bundleID = "net.sigmabeta.chipbox"
                iconFile.set(project.file("icons/chipbox.icns"))
            }
        }
    }
}

// Compose's prepareAppResources Sync reads appResourcesRootDir, so it must run after the staging
// Copy; the app-image / installer tasks consume it transitively.
tasks.matching {
    it.name == "prepareAppResources" || it.name == "createDistributable" || it.name.startsWith("package")
}.configureEach { dependsOn(stageAppResources) }

// Compose's `run` is a JavaExec, like the old application-plugin run. Wire the in-place native dir +
// splash with ABSOLUTE paths (the $APPDIR variants in app.jvmArgs are only valid once packaged).
// Compose registers `run` lazily (afterEvaluate), so match it via a live configureEach rather than
// tasks.named (which would resolve too early).
//
// The rewrite MUST run in doFirst (execution time), not at configuration time: Compose copies
// `application.jvmArgs` (the `-Djava.library.path=$APPDIR/resources` placeholder) onto the run task
// AFTER this configureEach fires, so a configuration-time `jvmArgs = …` gets clobbered and the JVM
// launches with the literal, unexpanded `$APPDIR/resources` on java.library.path — no emulator .so
// resolves and every track fails to load. doFirst runs last, so its override wins.
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    dependsOn(stageAppResources)
    val nativeDir = nativeLibsDirFile.absolutePath
    val splashPath = splashDebugFile.absolutePath
    // Dev runs are debug (window title/icon + swapped colors + purple splash); packaged installers
    // stay release (isDebug defaults false in JvmModules). -Dchipbox.debug=true opts this run in.
    doFirst {
        jvmArgs = jvmArgs.orEmpty()
            .filterNot { "java.library.path" in it || it.startsWith("-splash:") || "chipbox.debug" in it }
            .plus(
                listOf(
                    "-Djava.library.path=$nativeDir",
                    "-splash:$splashPath",
                    "-Dchipbox.debug=true",
                ),
            )
    }
}

dependencies {
    implementation(projects.cbox.common.crash.real)
    implementation(projects.cbox.common.player.generator.real)
    // SynthGenerator (the "fake" generator) selected by the debug generator-source setting.
    implementation(projects.cbox.common.player.generator.fake)
    implementation(projects.cbox.common.player.emulators.gba.real)
    implementation(projects.cbox.common.player.emulators.gme.real)
    implementation(projects.cbox.common.player.emulators.ncsf.real)
    implementation(projects.cbox.common.player.emulators.psf.real)
    implementation(projects.cbox.common.player.emulators.ssf.real)
    implementation(projects.cbox.common.player.emulators.twosf.real)
    implementation(projects.cbox.common.player.emulators.usf.real)
    implementation(projects.cbox.common.player.emulators.vgm.real)
    implementation(projects.cbox.common.player.emulators.vgmstream.real)
    implementation(projects.cbox.common.contentsource.api)
    implementation(projects.cbox.common.contentsource.file.real)
    implementation(projects.cbox.common.strings.real)
    implementation(projects.cbox.common.debug.api)
    implementation(projects.cbox.common.debug.real)
    implementation(projects.cbox.common.settings.api)
    implementation(projects.cbox.common.settings.real)
    // The JVM target now reuses the same `ChipboxAppUi` composable as Android — see
    // `cbox/android/appui/api` (now sage.kmp). `appui:api` already pulls in every feature
    // module via `api(...)`, so we don't list them individually here.
    implementation(projects.cbox.common.appui.api)

    // Direct deps for things the JVM-side Metro graph (JvmChipboxGraph) needs that aren't
    // covered transitively by appui.
    implementation(projects.features.settings.real)
    implementation(projects.features.library.real)
    implementation(projects.cbox.common.ui.list.api)
    implementation(projects.cbox.common.ui.chrome.api)
    // RealDirector — needed by every feature VM that depends on player.director.api
    // (NowPlaying, GameDetail, ArtistDetail, GamesForPlatform, BrowseAllTracks). All wired
    // into the appui module, so without a Director provider the JvmChipboxGraph fails to
    // resolve them.
    implementation(projects.cbox.common.player.director.real)
    // DirectorModule's `@ContributesTo(AppScope) @Provides` binds Director → RealDirector
    // for the Metro graph; without :di the binding isn't on the classpath and feature VMs
    // that take a Director fail to resolve.
    implementation(projects.cbox.common.player.director.di)
    // PlaybackSessionModule binds the session store + persister (save/restore last session) into
    // the graph; Main.kt restores on launch and snapshots on shutdown. Storage is JvmStorage.
    implementation(projects.cbox.common.player.persistence.di)
    // SourceDataLineSpeaker (the JVM/desktop Speaker) now lives in the shared player:speaker:real
    // module's jvmMain alongside the Android AudioTrack impl in androidMain.
    implementation(projects.cbox.common.player.speaker.real)
    implementation(projects.cbox.common.player.speaker.api)
    // FileSpeaker / TextSpeaker selected by the debug speaker-source setting.
    implementation(projects.cbox.common.player.speaker.fake)
    implementation(projects.cbox.common.player.resampler.di)
    implementation(projects.cbox.common.player.resampler.api)
    implementation(projects.cbox.common.player.buffer.real)
    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.models.api)
    // appDataDir() — resolves the standardized per-OS data directory under the user's home.
    implementation(projects.cbox.common.utils.api)
    // DebugInfoModule's `@ContributesTo(AppScope) @Provides` binds DebugInfoManager →
    // RealDebugInfoManager(director, generator, speaker, bufferDebugSource, scope), reached by
    // the PlaybackStatusViewModel now wired into the shared shell. `:di` is sage.di.jvm (the
    // Android app pulls the same module), so no variant-resolution trouble on the JVM classpath.
    // BufferDebugSource + CoroutineScope are supplied by JvmModules.kt.
    implementation(projects.cbox.common.debugInfo.di)

    // Room KMP database used as the JVM target's real library. `sqlite-bundled` is the
    // cross-platform Room driver Android doesn't need.
    implementation(projects.cbox.common.repository.real)
    // Provides the Memory + Random repository bindings (RepositoryModule), selected by the debug
    // "repository source" setting in JvmRepositoryModule. api-exposes repository.fake's types too.
    implementation(projects.cbox.common.repository.di)
    implementation(projects.cbox.common.database.real)
    implementation(libs.sqlite.bundled)

    // Playback history: JvmHistoryModule provides the HistoryDatabase; the shared history/di module
    // provides the repository + recorder consumed by JvmChipboxGraph / Main.kt.
    implementation(projects.cbox.common.history.real)
    implementation(projects.cbox.common.history.di)

    // Favorites: JvmFavoritesModule provides the FavoritesDatabase; the shared favorites/di module
    // provides the repository consumed by the feature VMs and the director's favorites session.
    implementation(projects.cbox.common.favorites.real)
    implementation(projects.cbox.common.favorites.di)

    // Playlists: JvmPlaylistsModule provides the PlaylistsDatabase; the shared playlists/di module
    // provides the repository consumed by the playlists feature VMs.
    implementation(projects.cbox.common.playlists.real)
    implementation(projects.cbox.common.playlists.di)

    // Shared scanner — uses the new LibrarySource interface; LocalFileContentSource
    // is the JVM impl, the Android twin is AndroidFileContentSource (SAF).
    implementation(projects.cbox.common.scanner.real)
    implementation(projects.cbox.common.readers.api)

    // Metro DI — JvmChipboxGraph is the runtime DI root (replaces the plain-Dagger
    // JvmChipboxComponent dropped at M6). AppScope lives in sage/common/di;
    // metrox-viewmodel(-compose) backs metroViewModel<T>() on the desktop UI.
    implementation(libs.sage.common.di)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)

    implementation(libs.sage.common.logging)
    // LocalLogger composition local (provided in DesktopMain) lives here.
    implementation(libs.sage.common.ui.perfCompose)
    implementation(libs.sage.common.appinfo)
    implementation(libs.sage.common.storage.common)
    implementation(libs.kotlinx.coroutines.core)
    // Dispatchers.Main on the JVM target = AWT event queue. SettingsViewModel and any other
    // ChipboxListViewModel scheduling onto viewModelScope (default = Main) needs this runtime.
    implementation(libs.kotlinx.coroutines.swing)

    // Compose Multiplatform desktop. The platform-agnostic libs come from the catalog so
    // shared UI modules (future sage.compose.kmp) pin the exact same versions; the per-OS
    // Skia native is pulled via `compose.desktop.currentOs` from the JetBrains plugin
    // extension applied above. Bootstrap inlines its Hello composable in DesktopMain.kt;
    // these deps will mostly move into shared modules in follow-up slices.
    implementation(libs.jetbrains.compose.runtime)
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.ui)
    implementation(compose.desktop.currentOs)

    // Shared Chipbox color schemes + typography + ChipboxTheme — same palette / type structure
    // the Android UI uses via cbox/android/ui/theme/api. Transitively pulls
    // cbox/common/ui/fonts/api (via api()), so the JVM target gets real Chipbox pixel-art
    // fonts via Compose Multiplatform resources.
    implementation(projects.cbox.common.ui.theme.api)

    // androidx.lifecycle.ViewModel for HelloViewModel (Metro VMs extend ViewModel directly
    // post-M6 — the old ChipboxViewModel marker class in cbox/common/ui/vm/api was for the
    // pre-Metro chipboxViewModel<T>() multiplatform machinery, now deleted).
    implementation(libs.androidx.lifecycle.viewmodel)

    // Voyager — Compose Multiplatform navigation. Replaces what would otherwise be
    // androidx.navigation:navigation-compose (the AndroidX CMP fork publishes JVM stubs
    // only, no runtime). Each screen implements `Screen`; `Navigator(MyScreen())` owns
    // the back stack.
    implementation(libs.voyager.navigator)

    // OS media-control integration (net.sigmabeta.chipbox.jvm.mediasession). dbus-java speaks the
    // session-bus MPRIS protocol the Linux desktop uses for media keys / the system media widget.
    // Inline coordinates (not the catalog) because the version catalog lives in the `sage`
    // submodule and this is a chipbox-only, desktop-only dependency — same pattern as the
    // kotlinx-datetime deps in cbox/common/*. The native-unixsocket transport uses JDK 17's
    // UnixDomainSocketAddress (no JNI). Other OSes have no backend yet, so neither artifact is
    // touched outside Linux.
    implementation("com.github.hypfvieh:dbus-java-core:5.0.0")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.0.0")

    // Unit tests for the media-session bridge (MprisState / MprisMediaControls). kotlin-test
    // resolves to its JUnit 4 variant via the junit4 dep below; the director fake feeds the
    // bridge's inbound transport methods. No coroutines-test needed — the pure state model and
    // the action methods are exercised directly without a live bus.
    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    testImplementation(projects.cbox.common.player.director.fake)
}
