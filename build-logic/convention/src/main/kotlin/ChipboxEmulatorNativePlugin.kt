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

            extensions.configure<LibraryExtension> {
                this.namespace = namespace
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
