import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import dev.zacsweers.metro.gradle.DelicateMetroGradleApi
import dev.zacsweers.metro.gradle.ExperimentalMetroGradleApi
import dev.zacsweers.metro.gradle.MetroPluginExtension
import dev.zacsweers.metro.gradle.RequiresIdeSupport
import net.sigmabeta.sage.plugins.components.namespaceFromPath
import net.sigmabeta.sage.plugins.components.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

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
    @OptIn(DelicateMetroGradleApi::class, ExperimentalMetroGradleApi::class, RequiresIdeSupport::class)
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("sage.kmp")
                apply("sage.compose.kmp")
                apply("dev.zacsweers.metro")
            }

            // Metro's hint / top-level-injection codegen emits top-level declarations that
            // Kotlin/JS incremental compilation rejects (KT-82395). Every feature :real module
            // carries an enforcement-only js() purity gate, so gate hint generation to the real
            // JVM/Android targets (omitting JS, which has no runtime DI graph here).
            extensions.configure<MetroPluginExtension> {
                enableTopLevelFunctionInjection.set(false)
                generateContributionHintsInFir.set(false)
                supportedHintContributionPlatforms.set(
                    setOf(KotlinPlatformType.jvm, KotlinPlatformType.androidJvm),
                )
            }

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
