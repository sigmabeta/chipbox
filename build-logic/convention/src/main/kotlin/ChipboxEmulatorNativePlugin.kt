import com.android.build.api.dsl.LibraryExtension
import net.sigmabeta.sage.plugins.components.namespaceFromPath
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention for `:cbox:android:player:emulators:<emu>:native` modules — the Android-only
 * companion that runs the emulator's CMake build. Lives separately from `:real` because AGP 9's
 * KMP library plugin has no `externalNativeBuild` DSL.
 *
 * The CMake path is derived from the project's filesystem location, not its Gradle path: the
 * `2sf` emulator is the only case where the two diverge (the Gradle project name is `twosf`
 * because identifiers can't start with a digit, while the directory is still `2sf`).
 */
class ChipboxEmulatorNativePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("sage.android")

            val emulatorDirName = project.projectDir.parentFile.name
            val namespace = namespaceFromPath()

            // Declaring externalNativeBuild resolves the CMake/NDK toolchain at *configuration*
            // time — ~20s of one-time provisioning that the first-configured native module
            // absorbs. Jobs that never build native (ktlint, detekt configure every project)
            // pass -Pchipbox.skipNative to skip it; local/default builds and the native jobs
            // (assemble/lint/installDist) keep configuring it. These modules have no Kotlin
            // source, so skipping the native DSL leaves nothing for those jobs to do here.
            val skipNative = providers.gradleProperty("chipbox.skipNative").isPresent

            extensions.configure<LibraryExtension> {
                this.namespace = namespace
                if (!skipNative) {
                    defaultConfig {
                        ndk {
                            // 64-bit ABIs only. Google Play has required a 64-bit build since
                            // Aug 2019 and 32-bit-only devices are effectively gone, so dropping
                            // armeabi-v7a + x86 halves emulator native build time and APK size.
                            // AGP intersects this set with the CMake build, so the 32-bit slices
                            // are never compiled. Re-add an ABI here if one is ever needed again.
                            abiFilters += setOf("arm64-v8a", "x86_64")
                        }
                    }
                    externalNativeBuild {
                        cmake {
                            path = rootProject.file("cbox/native/$emulatorDirName/CMakeLists.txt")
                            version = "3.22.1"
                        }
                    }
                }
            }
        }
    }
}
