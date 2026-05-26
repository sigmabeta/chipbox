plugins {
    alias(libs.plugins.sage.kmp)
}

// DatabaseRepository is pure Kotlin — it now takes the @Dao interfaces (database/api, which is
// JS-able on room-common) directly instead of the Room @Database, so it builds for the js()
// purity gate too. The Room runtime (database/real) only appears in DI, which extracts the DAOs
// from ChipboxDatabase and hands them here.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.repository.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.repository.api)
                api(projects.cbox.common.database.api)
                implementation(projects.cbox.common.perf.api)
                implementation(projects.cbox.common.utils.api)
                implementation(libs.sage.common.logging)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
                // No alias in libs.versions.toml yet; pin to the same coroutines version as core
                // so runTest / TestScope APIs line up exactly with the production runtime.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            }
        }
    }
}
