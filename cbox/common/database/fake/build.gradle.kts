plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// In-memory implementations of the 6 DAO interfaces from :database:api. Used by
// DatabaseRepositoryTest in :repository:real — production Room runtime stays in :database:real,
// these are pure-Kotlin map-backed doubles that mimic the Flow-re-emits-on-table-change
// contract.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.database.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.database.api)
                api(projects.cbox.common.entities.api)

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
