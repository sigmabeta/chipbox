import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import net.sigmabeta.sage.plugins.components.namespaceFromPath
import net.sigmabeta.sage.plugins.components.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for `:features:<x>:real` modules.
 *
 * Layers the SAGE `sage.kmp` + `sage.compose.kmp` plugins and the Metro DI compiler plugin, and
 * adds the three deps every chipbox feature `:real` reaches for: `sage.common.di` (Metro scope
 * contributions), `metrox.viewmodel` + `metrox.viewmodel.compose` (the `@HiltViewModel` /
 * `hiltViewModel()` replacement after the Hilt → Metro migration).
 *
 * Per-feature `cbox.android.ui.*` / `cbox.common.player.*` deps stay in each module's build
 * file — they differ enough that consolidating them would hide real coupling.
 */
class ChipboxFeatureRealPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("sage.kmp")
                apply("sage.kmp.js")
                apply("sage.compose.kmp")
                apply("dev.zacsweers.metro")
            }

            // KT-82395 ("Kotlin/JS does not support generating top-level declarations with
            // incremental compilation enabled") was fixed in Kotlin 2.3.21. Before that, every
            // feature :real module gated `supportedHintContributionPlatforms` to {jvm, androidJvm}
            // and disabled the related codegen flags. Now that the underlying compiler bug is
            // gone, JS hints can generate alongside JVM/Android — which is what lets `apps/js`
            // build a real Metro `@DependencyGraph` instead of hand-wiring providers.

            val derivedNamespace = namespaceFromPath()
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure(
                    KotlinMultiplatformAndroidLibraryExtension::class.java,
                ) {
                    namespace = derivedNamespace
                }

                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.findLibrary("sage.common.di").get())
                    implementation(libs.findLibrary("metrox.viewmodel").get())
                    implementation(libs.findLibrary("metrox.viewmodel.compose").get())
                }
            }
        }
    }
}
