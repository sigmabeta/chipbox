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
    application
}

application {
    mainClass.set("net.sigmabeta.chipbox.jvm.MainKt")
    applicationName = "chipbox"
}

// Host emulator native libs are built by the chipbox.native.host plugin (the shared cacheable
// build in build-logic) into build/jvm-native/libs/ via the `chipboxHostNativeLibs` aggregate
// task. run / installDist / the start scripts wire to that directory + task below.
val splashImageFile: File = layout.projectDirectory.dir("src/main/splash").file("splash.png").asFile
val nativeLibsDirFile: File = layout.buildDirectory.dir("jvm-native/libs").get().asFile
val nativeLibsTask = tasks.named("chipboxHostNativeLibs")

tasks.named<JavaExec>("run") {
    dependsOn(nativeLibsTask)
    systemProperty("java.library.path", nativeLibsDirFile.absolutePath)
    // `-splash:` is a launcher JVM arg (not a -D property); shows the splash before main() runs.
    jvmArgs("-splash:${splashImageFile.absolutePath}")
}

// Bundle the emulator .so/.dylib/.dll files into `lib/native/` inside the distribution so
// `applicationDefaultJvmArgs`' `__APP_HOME__/lib/native` resolves at launch. `installDist`,
// `distZip`, and `distTar` all consume this. Path-derived archive names from the SAGE
// convention plugins (`sage.kmp` / `sage.jvm`) keep the per-module jars unique in `lib/`,
// so the flat distribution layout works without the old `standaloneScript` workaround.
distributions {
    named("main") {
        contents {
            from(nativeLibsDirFile) {
                into("lib/native")
            }
            // Loose file (not inside a jar) so the `-splash:$APP_HOME/lib/splash.png` launcher
            // flag injected into the start scripts below can read it.
            from(splashImageFile) {
                into("lib")
            }
        }
    }
}
listOf("installDist", "distZip", "distTar").forEach { taskName ->
    tasks.named(taskName) { dependsOn(nativeLibsTask) }
}

// Inject `-Djava.library.path=…/lib/native` directly into the exec line of each generated
// start script. Routing through `applicationDefaultJvmArgs` doesn't work for native paths:
// Gradle's unix template puts JVM args through an xargs+sed escape pipeline that turns `$`
// into `\$`, so the shell never expands `$APP_HOME` and the JVM ends up looking for a
// literal "APP_HOME" directory. Injecting after the exec line is the only place where the
// shells (bash `$APP_HOME`, cmd `%APP_HOME%`) actually interpolate the install root.
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        unixScript.writeText(
            unixScript.readText().replace(
                "exec \"\$JAVACMD\" \"\$@\"",
                "exec \"\$JAVACMD\" \"-Djava.library.path=\$APP_HOME/lib/native\" \"\$@\"",
            )
        )
        // The bat template's exec line ends in `%CMD_LINE_ARGS%` (Gradle ≤ 8.x) or `%*`
        // (current). Match the `-classpath` segment instead — stable across both — and
        // splice the system property in just before it.
        windowsScript.writeText(
            windowsScript.readText().replace(
                "-classpath \"%CLASSPATH%\"",
                "-D\"java.library.path=%APP_HOME%\\lib\\native\" -classpath \"%CLASSPATH%\"",
            )
        )
        // Launcher splash (covers the synchronous graph build before the window opens). Anchored
        // on the `-Djava.library.path=…` token the replaces above just inserted — NOT the original
        // exec line, which no longer exists at this point. `$APP_HOME/lib/splash.png` is bundled by
        // the `distributions` block; the path interpolates in the shell, same as java.library.path.
        unixScript.writeText(
            unixScript.readText().replace(
                "\"-Djava.library.path=\$APP_HOME/lib/native\" \"\$@\"",
                "\"-Djava.library.path=\$APP_HOME/lib/native\" \"-splash:\$APP_HOME/lib/splash.png\" \"\$@\"",
            )
        )
        windowsScript.writeText(
            windowsScript.readText().replace(
                "-D\"java.library.path=%APP_HOME%\\lib\\native\" -classpath \"%CLASSPATH%\"",
                "-splash:\"%APP_HOME%\\lib\\splash.png\" " +
                    "-D\"java.library.path=%APP_HOME%\\lib\\native\" -classpath \"%CLASSPATH%\"",
            )
        )
    }
}

dependencies {
    implementation(projects.cbox.common.crash.real)
    implementation(projects.cbox.common.player.generator.real)
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
    implementation(projects.cbox.common.database.real)
    implementation(libs.sqlite.bundled)

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
