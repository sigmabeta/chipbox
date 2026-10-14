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
    mainClass.set("net.sigmabeta.chipbox.perfbench.MainKt")
    applicationName = "perfbench"
}

// The host audio axis drives the emulator cores directly, so it needs the same host-built `.so`
// files on java.library.path that `:apps:abrender` uses. Reuse apps/jvm's host-CMake output via its
// consumable configuration rather than reading its layout (forbidden under Isolated Projects);
// resolving it pulls `chipboxHostNativeLibs` as a task dependency through `builtBy`.
val jvmNativeLibsDeps: Configuration by configurations.dependencyScope("jvmNativeLibsDeps")
val jvmNativeLibs: Configuration by configurations.resolvable("jvmNativeLibs") {
    extendsFrom(jvmNativeLibsDeps)
}
dependencies {
    add(jvmNativeLibsDeps.name, project(path = ":apps:jvm", configuration = "hostNativeLibsElements"))
}
class NativeLibraryPathArgument(
    @get:InputFiles @get:PathSensitive(PathSensitivity.ABSOLUTE) val nativeDir: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Djava.library.path=${nativeDir.singleFile.absolutePath}")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    jvmArgumentProviders.add(NativeLibraryPathArgument(jvmNativeLibs))
}

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

// Same Compose/skiko exclusions as :apps:abrender — the emulator/repository graph drags the desktop
// Compose runtime onto the runtime classpath, and its duplicate skiko artifacts break the dist Copy.
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
    implementation(projects.apps.perfbenchCore)
    // The raw-emulator-core render path (EmulatorBank) and corpus walker reused unchanged.
    implementation(projects.apps.abrenderCore)

    // `snapshot` runs the real host scanner over a corpus — the same scan stack the headless CLI
    // (apps/cli) assembles by hand: RealScanner, the pure-Kotlin readers, and an okio file walker.
    // The scan's raw output is captured by a write-only Repository in the command itself (no
    // database), so no repository implementation module is needed.
    implementation(projects.cbox.common.scanner.real)
    implementation(projects.cbox.common.scanner.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.contentsource.api)
    implementation(projects.cbox.common.contentsource.file.real)
    implementation(projects.cbox.common.readers.api)
    implementation(projects.cbox.common.models.api)
    implementation(projects.cbox.common.player.emulators.api)
    implementation(projects.cbox.common.player.emulators.vgmstream.real)
    implementation(libs.sage.common.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okio)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    // The round-trip test imports a generated snapshot into the production DatabaseRepository over
    // the in-memory DAO fakes, then compares it to scanning the same corpus directly.
    testImplementation(projects.cbox.common.repository.real)
    testImplementation(projects.cbox.common.database.fake)
}
