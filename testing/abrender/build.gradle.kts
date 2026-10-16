import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.sage.jvm)
}

// The A/B render harness drives EVERY native emulator directly (USF, PSF, SSF, GME, ...), so unlike
// the CLI — which only needs libvgmstream.so for scan-time subsong probing — it needs the whole set
// of `.so` files on java.library.path. Rather than duplicate apps/jvm's host-CMake machinery, reuse
// the libs that module already builds: depend on its aggregate `chipboxHostNativeLibs` task and
// point java.library.path at its output dir (build/jvm-native/libs).
// Consume every host-built emulator .so from apps/jvm via a named consumable configuration rather
// than reading project(":apps:jvm").layout directly (forbidden under Isolated Projects). Resolving
// this configuration yields the output directory and pulls its `builtBy` task as a dependency.
val jvmNativeLibsDeps: Configuration by configurations.dependencyScope("jvmNativeLibsDeps")
val jvmNativeLibs: Configuration by configurations.resolvable("jvmNativeLibs") {
    extendsFrom(jvmNativeLibsDeps)
}
dependencies {
    add(jvmNativeLibsDeps.name, project(path = ":apps:jvm", configuration = "hostNativeLibsElements"))
}
// Argument provider that resolves the native-lib directory lazily at execution and emits it as
// `-Djava.library.path`. Modelling it as a CommandLineArgumentProvider with an @InputFiles
// FileCollection (rather than a lambda capturing a configuration-backed Provider) keeps it
// configuration-cache safe and establishes the task dependency on the config's builtBy producer.
class NativeLibraryPathArgument(
    @get:InputFiles @get:PathSensitive(PathSensitivity.ABSOLUTE) val nativeDir: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Djava.library.path=${nativeDir.singleFile.absolutePath}")
}

// The CLI (`render`/`diff`) is developer tooling, not a shipped app — an explicit JavaExec rather
// than the `application` plugin (no distribution, no start scripts). It drives the same engine the
// record.sh/verify.sh scripts do, and needs the host emulator `.so`s on java.library.path.
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the abrender CLI (render/diff over a corpus)."
    mainClass.set("net.sigmabeta.chipbox.abrender.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    jvmArgumentProviders.add(NativeLibraryPathArgument(jvmNativeLibs))
}

// Stages a runnable classpath + the host `.so`s under build/abrender-runtime/ for record.sh's
// --parallel host mode. That mode launches several JVMs concurrently — it cannot go through Gradle
// (the daemon serializes builds), so it needs a plain classpath + native dir instead of `run`. This
// is a build artifact for a script, not a distribution; the old `application` installDist is gone.
// The `lib/` dir must hold a JAR for EVERY class on the path — including this module's own CLI
// (MainKt), which `configurations.runtimeClasspath` (dependencies only) omits — because `java -cp
// "lib/*"` expands the wildcard to JARs, not class directories.
tasks.register<Sync>("abrenderRuntime") {
    group = "application"
    description = "Stage the abrender runtime (classpath + native libs) for record.sh --parallel."
    into(layout.buildDirectory.dir("abrender-runtime"))
    from(configurations.runtimeClasspath) { into("lib") }
    from(tasks.named("jar")) { into("lib") }
    from(jvmNativeLibs) {
        include("*.so")
        into("native")
    }
}

// The harness is headless and never renders Compose, but models.api (transitively pulled by
// repository/emulators) declares Compose UI deps via the sage.compose.kmp convention plugin, which
// drags the desktop Compose runtime + skiko (~13 MB) onto the runtime classpath. None of it is
// reachable here, and the duplicate skiko artifacts otherwise break the staged-runtime Copy —
// exclude them at the configuration level, mirroring apps/cli.
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.compose.foundation")
    exclude(group = "org.jetbrains.compose.material")
    exclude(group = "org.jetbrains.compose.material3")
    exclude(group = "org.jetbrains.compose.ui")
    exclude(group = "org.jetbrains.compose.animation")
    exclude(group = "org.jetbrains.compose.components", module = "components-resources")
    exclude(group = "org.jetbrains.skiko")
}

dependencies {
    // Render loop, metrics, WAV writer, and corpus walker — plus, transitively, every emulator JNI
    // wrapper (object XxxEmulator → System.loadLibrary) and EbuR128. This module keeps only the CLI
    // shell (Main, DiffCommand); the engine it drives lives in :testing:abrender-core so the same code
    // also renders on-device. The JVM variant of the core pulls the JVM JNI wrappers; the .so files
    // come from :apps:jvm's host build via java.library.path (configured above).
    implementation(projects.testing.abrenderCore)

    // GoldenReportToleranceTest — the diff --check tolerance semantics (GoldenCheck.kt in the core).
    testImplementation(libs.junit4)
}
