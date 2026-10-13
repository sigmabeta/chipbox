import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.plugins.ExtensionAware

/*
 * The shared perfbench contract — result schema, canonical scenario IDs, fingerprint, corpus
 * manifest. Factored into its own KMP module so both homes of the harness can depend on it:
 * the JVM CLI in `:apps:perfbench` (host axes) and the Android `:benchmark` module (device axes).
 * Keeping the serialized shape here means a result written by one home can be read by the other.
 *
 * Pure contract only: no renderer, no I/O. Serialization is `api` because the schema types carry
 * generated serializers that any consumer needs resolvable. The library-snapshot DTO and its ingest
 * capability are a repository concern and live in `:cbox:common:repository:api`.
 */
plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    (this as ExtensionAware).extensions.configure(
        KotlinMultiplatformAndroidLibraryExtension::class.java,
    ) {
        namespace = "net.sigmabeta.chipbox.perfbench"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.kotlinx.serialization.json)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
