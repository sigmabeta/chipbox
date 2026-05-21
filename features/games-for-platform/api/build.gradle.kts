plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.features.gamesforplatform.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(libs.kotlinx.serialization.core)
                implementation(projects.cbox.common.models.api)
            }
        }
    }
}
