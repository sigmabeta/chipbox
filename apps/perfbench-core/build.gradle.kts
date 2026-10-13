import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.plugins.ExtensionAware

/*
 * The shared perfbench contract — result schema, canonical scenario IDs, fingerprint, corpus
 * manifest. Factored into its own KMP module so both homes of the harness can depend on it:
 * the JVM CLI in `:apps:perfbench` (host axes) and the Android `:benchmark` module (device axes).
 * Keeping the serialized shape here means a result written by one home can be read by the other.
 *
 * Pure contract only: no renderer, no I/O. Serialization is `api` because the schema types carry
 * generated serializers that any consumer needs resolvable.
 */
plugins {
    alias(libs.plugins.sage.kmp)
    // JS as a purity cross-check only (added under -Psage.js) — the app's Settings feature, which
    // consumes the snapshot-ingest capability, builds for JS too, so this contract must resolve a
    // JS variant like every other shared module.
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.kotlin.serialization)
    id("chipbox.kmp.test")
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
                // The snapshot DTO serializes the scanner's raw output types (RawGame/RawTrack),
                // which are annotated @Serializable in this module.
                api(projects.cbox.common.repository.api)
            }
        }

        named("commonTest") {
            dependencies {
                // Import-semantics tests run against the production DatabaseRepository over the
                // in-memory DAO fakes — the same in-memory fake-database rig the repository's own
                // DatabaseRepositoryTest uses.
                implementation(projects.cbox.common.repository.real)
                implementation(projects.cbox.common.database.fake)
                implementation(libs.sage.common.logging)
            }
        }
    }
}
