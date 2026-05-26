import net.sigmabeta.sage.plugins.components.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Opt-in `commonTest` source-set wiring for chipbox KMP modules.
 *
 * Adds `kotlin("test")`, `kotlinx.coroutines.core`, and `kotlinx.coroutines.test` to a module's
 * commonTest dependencies — the rig every `runTest { … }` + `Dispatchers.setMain(…)` test in
 * this codebase needs. Apply alongside `sage.kmp` (or `chipbox.feature.real`) in any module
 * with tests:
 *
 *     plugins {
 *         alias(libs.plugins.sage.kmp)
 *         id("chipbox.kmp.test")
 *     }
 *
 * Per-module test deps (fakes, okio-fakefilesystem, datetime pin, etc.) still live in each
 * module's own `commonTest` dependencies block.
 *
 * This plugin assumes the underlying multiplatform extension exists — apply it after the KMP
 * plugin (typically `sage.kmp`, which lives behind `chipbox.feature.real`). Doesn't introduce
 * any compile-time deps on commonMain — modules that don't run tests pay only the configuration
 * cost of one extra source-set lookup.
 */
class ChipboxKmpTestPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonTest").dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.findLibrary("kotlinx.coroutines.core").get())
                    implementation(libs.findLibrary("kotlinx.coroutines.test").get())
                }
            }
        }
    }
}
