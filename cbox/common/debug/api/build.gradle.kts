plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.debug.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
