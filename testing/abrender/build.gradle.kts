import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
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

// Emits the golden-check system properties (see the record/verify tasks below) lazily, at execution
// time. A named CommandLineArgumentProvider rather than a script-level doFirst closure: the latter
// captures a Gradle script object and breaks the configuration cache. A missing corpus is reported
// here, with the fix, rather than as an opaque input-validation error.
class GoldenArgumentProvider(
    @get:Input @get:Optional val corpus: Provider<String>,
    @get:Input @get:Optional val dir: Provider<String>,
    @get:Input val work: Provider<String>,
    @get:Input @get:Optional val seconds: Provider<String>,
    @get:Input @get:Optional val ext: Provider<String>,
    @get:Input @get:Optional val game: Provider<String>,
    @get:Input @get:Optional val everySong: Provider<String>,
    @get:Input @get:Optional val limit: Provider<String>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        val corpusDir = corpus.orNull ?: throw GradleException(
            "no corpus: pass -Pchipbox.golden.corpus=<corpus dir> " +
                "(the golden check renders and compares the audio you supply)",
        )
        return buildList {
            add("-Dchipbox.golden.corpus=$corpusDir")
            dir.orNull?.let { add("-Dchipbox.golden.dir=$it") }
            add("-Dchipbox.golden.work=${work.get()}")
            seconds.orNull?.let { add("-Dchipbox.golden.seconds=$it") }
            ext.orNull?.let { add("-Dchipbox.golden.ext=$it") }
            game.orNull?.let { add("-Dchipbox.golden.game=$it") }
            everySong.orNull?.let { add("-Dchipbox.golden.every-song=$it") }
            limit.orNull?.let { add("-Dchipbox.golden.limit=$it") }
        }
    }
}

// The CLI (`render`/`diff`) is developer tooling, not a shipped app — an explicit JavaExec rather
// than the `application` plugin (no distribution, no start scripts). It drives the same engine the
// golden tests do, and needs the host emulator `.so`s on java.library.path.
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the abrender CLI (render/diff over a corpus)."
    mainClass.set("net.sigmabeta.chipbox.abrender.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    jvmArgumentProviders.add(NativeLibraryPathArgument(jvmNativeLibs))
}

// --- Bit-identity golden PCM checks (record/verify) ------------------------------------------------
// The screenshot-test analog for the native cores: render a caller-supplied corpus and either record
// the reference PCM or fail unless the fresh render is bit-identical to a previously recorded one.
// Goldens are local-only and git-ignored (the corpus + PCM may not be redistributable), so the audio
// is passed by property and the reference defaults to <repo>/ab-goldens:
//
//   ./gradlew :testing:abrender:recordPcmGolden -Pchipbox.golden.corpus=/path/to/corpus
//   ./gradlew :testing:abrender:verifyPcmGolden -Pchipbox.golden.corpus=/path/to/corpus
//
// Both reuse the host emulator `.so`s (via jvmNativeLibs) exactly as the `run` task does. The same
// check runs on-device (arm64) against the same reference — see testing/abrender-core's
// PcmGoldenDeviceTest and golden-on-device.sh.
val goldenCorpus = providers.gradleProperty("chipbox.golden.corpus")
val goldenRoot = providers.gradleProperty("chipbox.golden.dir")
    .orElse(rootDir.resolve("ab-goldens").absolutePath)
val goldenWork = layout.buildDirectory.dir("pcm-golden-work").map { it.asFile.absolutePath }
val goldenSeconds = providers.gradleProperty("chipbox.golden.seconds")
val goldenExt = providers.gradleProperty("chipbox.golden.ext")
val goldenGame = providers.gradleProperty("chipbox.golden.game")
val goldenEverySong = providers.gradleProperty("chipbox.golden.every-song")
val goldenLimit = providers.gradleProperty("chipbox.golden.limit")

fun registerGoldenTask(taskName: String, method: String, describe: String) {
    tasks.register<Test>(taskName) {
        group = "verification"
        description = describe
        useJUnit()
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        filter.includeTestsMatching("net.sigmabeta.chipbox.abrender.PcmGoldenTest.$method")
        // A golden failure is a divergence report, not a stack trace: show render progress, the
        // per-track describe() message, and the assertion text — but no frames.
        testLogging {
            events("failed", "skipped")
            showStandardStreams = true
            exceptionFormat = TestExceptionFormat.FULL
            showStackTraces = false
        }
        // Same host .so dir + build dependency the `run` task uses, so a kernel change re-renders.
        jvmArgumentProviders.add(NativeLibraryPathArgument(jvmNativeLibs))
        jvmArgumentProviders.add(
            GoldenArgumentProvider(
                corpus = goldenCorpus,
                dir = goldenRoot,
                work = goldenWork,
                seconds = goldenSeconds,
                ext = goldenExt,
                game = goldenGame,
                everySong = goldenEverySong,
                limit = goldenLimit,
            ),
        )
    }
}

registerGoldenTask(
    "recordPcmGolden",
    "recordGolden",
    "Record golden PCM for a corpus (-Pchipbox.golden.corpus=<dir>).",
)
registerGoldenTask(
    "verifyPcmGolden",
    "verifyGolden",
    "Fail unless a corpus renders bit-identically to the recorded golden PCM.",
)

// Stages a runnable classpath + the host `.so`s under build/abrender-runtime/ for
// render-parallel.sh. That script launches several JVMs concurrently — it cannot go through Gradle
// (the daemon serializes builds), so it needs a plain classpath + native dir instead of `run`. This
// is a build artifact for a script, not a distribution; the old `application` installDist is gone.
tasks.register<Sync>("abrenderRuntime") {
    group = "application"
    description = "Stage the abrender runtime (classpath + native libs) for render-parallel.sh."
    into(layout.buildDirectory.dir("abrender-runtime"))
    from(configurations.runtimeClasspath) { into("lib") }
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

    // PcmGoldenTest — the host half of the bit-identity golden check (recordPcmGolden/verifyPcmGolden).
    testImplementation(libs.junit4)
}
