plugins {
    alias(libs.plugins.sage.jvm)
    application
}

application {
    mainClass.set("net.sigmabeta.chipbox.cli.MainKt")
    applicationName = "chipbox-cli"
}

// The scanner probes streamed-audio files through vgmstream's native lib (subsong enumeration +
// length) via VgmstreamProbe → System.loadLibrary("vgmstream"). It's the ONLY native emulator the
// CLI's scan path touches — every chiptune format is parsed by pure-Kotlin readers — so the CLI
// needs exactly libvgmstream.so on java.library.path. Rather than duplicate apps/jvm's host-CMake
// machinery (cmake/JDK probing, hostshim, per-emulator tasks), reuse the .so that module already
// builds: depend on its `nativeEmulatorVgmstream` task and point java.library.path at its output
// dir. (vgmstream needs no android/log hostshim, so that single task is self-sufficient here.)
val jvmNativeLibsDir = project(":apps:jvm").layout.projectDirectory.dir("libs").asFile
val buildVgmstreamLib = ":apps:jvm:nativeEmulatorVgmstream"

// Mordant's interactive folder picker enters raw terminal mode, which needs a real TTY on stdin.
// `gradlew run` forks a JVM whose stdin is detached by default; wiring System.in through lets the
// picker work when Gradle is invoked from a terminal (use `--console=plain`). The reliable way to
// drive the TUI, though, is the installed distribution:
//   ./gradlew :apps:cli:installDist  →  apps/cli/build/install/chipbox-cli/bin/chipbox-cli
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    dependsOn(buildVgmstreamLib)
    systemProperty("java.library.path", jvmNativeLibsDir.absolutePath)
}

// Bundle libvgmstream.so into `lib/native/` inside the distribution and inject
// `-Djava.library.path=$APP_HOME/lib/native` into the generated start scripts so the installed
// `chipbox-cli` finds the lib at launch — same approach as apps/jvm. Only libvgmstream.so is
// included; the other emulator .so files in apps/jvm/libs are playback-only and unused here.
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
listOf("installDist", "distZip", "distTar").forEach { taskName ->
    tasks.named(taskName) { dependsOn(buildVgmstreamLib) }
}
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        unixScript.writeText(
            unixScript.readText().replace(
                "exec \"\$JAVACMD\" \"\$@\"",
                "exec \"\$JAVACMD\" \"-Djava.library.path=\$APP_HOME/lib/native\" \"\$@\"",
            )
        )
        windowsScript.writeText(
            windowsScript.readText().replace(
                "-classpath \"%CLASSPATH%\"",
                "-D\"java.library.path=%APP_HOME%\\lib\\native\" -classpath \"%CLASSPATH%\"",
            )
        )
    }
}

dependencies {
    implementation(libs.mordant)

    // The real Chipbox scan + library stack — the same modules apps/jvm wires through Metro,
    // assembled by hand in ChipboxLibrary (the CLI needs only this slice, not the desktop graph).
    // RealScanner reconciles scanned folders into a bundled-SQLite Room DB via DatabaseRepository;
    // the CLI then queries that repository to browse games / artists / platforms / tracks.
    implementation(projects.cbox.common.scanner.real)
    // VgmstreamProbe (native subsong probe) — scanner.real takes the VgmstreamProber interface now,
    // so its native impl is wired in here (it's no longer pulled transitively via scanner.real).
    implementation(projects.cbox.common.player.emulators.vgmstream.real)
    implementation(projects.cbox.common.repository.real)
    implementation(projects.cbox.common.database.real)
    implementation(projects.cbox.common.scanner.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.contentsource.api)
    // LocalFileContentSource (JVM file walker) now lives in the shared contentsource:file:real
    // module's jvmMain alongside the Android SAF impl in androidMain.
    implementation(projects.cbox.common.contentsource.file.real)
    implementation(projects.cbox.common.readers.api)
    implementation(projects.cbox.common.models.api)
    // Platform/section display names: ChipboxStringProvider + loadChipboxStrings() preload the
    // single multiplatform string source (composeResources); StringProvider is the interface the
    // menus consume. strings.real exposes strings.api (ChipboxStringId / Platform.stringId) via api().
    implementation(projects.cbox.common.strings.real)
    implementation(libs.sage.common.ui.strings)
    implementation(libs.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.core)
    // "Get cover art" + "Organize library": the IGDB cover-art subsystem and the on-disk library
    // organizer now live in shared modules (cbox/common/coverart, cbox/common/organizer) so the
    // Android / JVM apps can reuse them too; the CLI just drives them from its Mordant menus.
    implementation(projects.cbox.common.coverart.api)
    implementation(projects.cbox.common.coverart.real)
    implementation(projects.cbox.common.organizer.api)
    implementation(projects.cbox.common.organizer.real)
    // GetCoverArt / OverrideCoverArt construct the OkHttpClient + OkHttpCoverArtHttp they hand to the
    // fetcher / IGDB client; okio Paths + FileSystem.SYSTEM feed the (now multiplatform) cover-art and
    // organizer modules, whose file I/O is okio rather than java.io.
    implementation(libs.okhttp)
    implementation(libs.okio)
    // BluntHatchet — the no-op Hatchet logger the scanner/repository/readers need to construct.
    implementation(libs.sage.common.logging)
}
