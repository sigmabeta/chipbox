plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(chipbox.plugins.kmp.test)
}

// DatabaseRepository is pure Kotlin — it now takes the @Dao interfaces (database/api, which is
// JS-able on room-common) directly instead of the Room @Database, so it builds for the js()
// purity gate too. The Room runtime (database/real) only appears in DI, which extracts the DAOs
// from ChipboxDatabase and hands them here.
kotlin {
    android {
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
                implementation(projects.cbox.common.database.fake)
            }
        }
    }
}
