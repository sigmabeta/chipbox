import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.sage.jvm)
}

// The host audio axis drives the emulator cores directly, so it needs the same host-built `.so`
// files on java.library.path that `:testing:abrender` uses. Reuse apps/jvm's host-CMake output via its
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

// perfbench is a measurement harness, not a shipped app: an explicit JavaExec rather than the
// `application` plugin (no distribution, no start scripts). The host axes drive the emulator cores
// directly, so they need the same host-built `.so`s on java.library.path.
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the perfbench CLI (audio/snapshot/diff)."
    mainClass.set("net.sigmabeta.chipbox.perfbench.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    jvmArgumentProviders.add(NativeLibraryPathArgument(jvmNativeLibs))
}

// Same Compose/skiko exclusions as :testing:abrender — the emulator/repository graph drags the desktop
// Compose runtime onto the runtime classpath, and its duplicate skiko artifacts otherwise break the
// staged-runtime Copy.
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
    implementation(projects.testing.perfbenchCore)
    // The raw-emulator-core render path (EmulatorBank) and corpus walker reused unchanged.
    implementation(projects.testing.abrenderCore)

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
