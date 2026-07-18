import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.sage.jvm)
    application
}

application {
    mainClass.set("net.sigmabeta.chipbox.abrender.MainKt")
    applicationName = "chipbox-abrender"
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

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    jvmArgumentProviders.add(NativeLibraryPathArgument(jvmNativeLibs))
}

// Bundle every emulator .so into `lib/native/` inside the distribution and inject
// `-Djava.library.path=$APP_HOME/lib/native` into the start scripts — same approach as apps/jvm and
// apps/cli, so the installed `chipbox-abrender` finds the libs at launch.
distributions {
    named("main") {
        contents {
            from(jvmNativeLibs) {
                include("*.so")
                into("lib/native")
            }
        }
    }
}
listOf("installDist", "distZip", "distTar").forEach { taskName ->
    tasks.named<AbstractCopyTask>(taskName) {
        dependsOn(jvmNativeLibs)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
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

// The harness is headless and never renders Compose, but models.api (transitively pulled by
// repository/emulators) declares Compose UI deps via the sage.compose.kmp convention plugin, which
// drags the desktop Compose runtime + skiko (~13 MB) onto the runtime classpath. None of it is
// reachable here, and the duplicate skiko artifacts otherwise break the dist Copy — exclude them at
// the configuration level, mirroring apps/cli.
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
    // shell (Main, DiffCommand); the engine it drives lives in :apps:abrender-core so the same code
    // also renders on-device. The JVM variant of the core pulls the JVM JNI wrappers; the .so files
    // come from :apps:jvm's host build via java.library.path (configured above).
    implementation(projects.apps.abrenderCore)
}
