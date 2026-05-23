plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("net.sigmabeta.chipbox.cli.MainKt")
    applicationName = "chipbox-cli"
}

// Mordant's interactive folder picker enters raw terminal mode, which needs a real TTY on stdin.
// `gradlew run` forks a JVM whose stdin is detached by default; wiring System.in through lets the
// picker work when Gradle is invoked from a terminal (use `--console=plain`). The reliable way to
// drive the TUI, though, is the installed distribution:
//   ./gradlew :apps:cli:installDist  →  apps/cli/build/install/chipbox-cli/bin/chipbox-cli
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
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
