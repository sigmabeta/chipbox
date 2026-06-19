import com.android.build.api.dsl.LibraryExtension
import net.sigmabeta.sage.plugins.components.namespaceFromPath
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.withType

/**
 * Convention for per-feature screenshot modules (`:features:<x>:screenshot`).
 *
 * Such a module renders a screen's SAGE [net.sigmabeta.sage.list.ListState] through the real
 * list pipeline with deterministic fake data and Paparazzi-snapshots it across a device matrix.
 * Each module only needs to declare its own sibling `:real` dependency — this plugin contributes
 * the namespace, the previews scaffolding module, the chipbox models module, and the SAGE
 * appcomm/list libraries every screenshot test needs.
 *
 * Disables the JUnit test reports — Paparazzi 2.0.0-alpha04's HTML reporter calls
 * `TestResultsProvider.hasOutput(...)`, which Gradle 9.x removed; snapshots still record/verify
 * in the test JVM, only the post-run HTML summary crashes the task. Remove the report opt-out
 * once Paparazzi ships a Gradle-9 compatible build.
 */
class ChipboxScreenshotPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("sage.android")
                apply("sage.compose.android")
                apply("app.cash.paparazzi")
            }

            extensions.configure<LibraryExtension> {
                namespace = namespaceFromPath()
            }

            dependencies {
                add("implementation", "net.sigmabeta.sage:appcomm")
                add("implementation", "net.sigmabeta.sage:list")
                add("implementation", project(":cbox:android:ui:previews"))
                add("implementation", project(":cbox:common:models:api"))

                // CMP 1.11 packages androidMain `composeResources` as Android assets only, off the
                // JVM unit-test classpath — so under Paparazzi the `ClasspathResourceReader` in
                // ChipboxPreviewStrings can't find the `.cvr` files and every string/font resource
                // falls back to its key/default (rendering resource names). Each resource-owning module
                // exposes a `composeResourcesElements` configuration: a classpath-shaped jar of its
                // composeResources (see those modules' build.gradle.kts). Pulling them as test deps puts
                // those `.cvr`/font files back on the unit-test classpath the way the metrox library's
                // own composeResources arrive. `project(path, configuration=...)` is a declarative
                // cross-project dependency — config-cache-safe, unlike a cross-project task reference —
                // and the jar carries its own build dependency, so it's always built first.
                listOf(":cbox:common:strings:real", ":cbox:common:ui:fonts:real").forEach { modulePath ->
                    add(
                        "testImplementation",
                        project(mapOf("path" to modulePath, "configuration" to "composeResourcesElements")),
                    )
                }
            }

            tasks.withType<Test>().configureEach {
                reports.html.required.set(false)
                reports.junitXml.required.set(false)
            }
        }
    }
}
