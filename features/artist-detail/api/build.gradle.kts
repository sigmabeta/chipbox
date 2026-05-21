plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.features.artistdetail.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(libs.kotlinx.serialization.core)
            }
        }
    }
}
