plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.repository.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.models.api)
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
