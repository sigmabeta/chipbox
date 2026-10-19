plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.notif.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.strings.api)

                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.core)
            }
        }
    }
}
