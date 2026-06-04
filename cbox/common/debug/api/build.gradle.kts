plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    android {
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
