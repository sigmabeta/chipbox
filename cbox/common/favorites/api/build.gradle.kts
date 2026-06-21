plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.favorites.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.room.common)
                api(projects.cbox.common.entities.api)
                api(projects.cbox.common.models.api)
                // Flow appears in the DAO + repository public API, so expose it transitively.
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
