plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(projects.cbox.android.scanner.real)
    implementation(projects.cbox.android.repository.real)
    implementation(projects.cbox.android.database.all)
    implementation(projects.cbox.common.scanner.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.contentsource.api)
    implementation(projects.cbox.common.readers.api)
    implementation(projects.cbox.common.models.api)
    // Platform display names: ChipboxStringId (the Platform.stringId values) + the StringProvider
    // interface the CLI's CliStringProvider implements.
    implementation(projects.cbox.common.strings.api)
    implementation(libs.sage.common.ui.strings)
    implementation(libs.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.core)
    // "Get cover art": OkHttp talks to the Twitch token + IGDB search/image endpoints, and
    // kotlinx-serialization parses their JSON responses.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // BluntHatchet — the no-op Hatchet logger the scanner/repository/readers need to construct.
    implementation(libs.sage.common.logging)
}
