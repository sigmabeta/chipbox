import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import net.sigmabeta.sage.plugins.components.namespaceFromPath
import net.sigmabeta.sage.plugins.components.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for `:features:<x>:api` modules.
 *
 * Layers on the SAGE `sage.kmp` plugin and the Kotlin serialization plugin (route keys are
 * `@Serializable data object/data class`), adds `kotlinx-serialization-core` to `commonMain`,
 * and derives the module's namespace from its Gradle path via [namespaceFromPath].
 *
 * Modules that need extra `:api`-exported dependencies still declare them in their own
 * build file; this plugin only contributes the boilerplate that every feature `:api` shares.
 */
class ChipboxFeatureApiPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("sage.kmp")
                apply("sage.kmp.js")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            val derivedNamespace = namespaceFromPath()
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure(
                    KotlinMultiplatformAndroidLibraryExtension::class.java,
                ) {
                    namespace = derivedNamespace
                }

                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.findLibrary("kotlinx.serialization.core").get())
                }
            }
        }
    }
}
