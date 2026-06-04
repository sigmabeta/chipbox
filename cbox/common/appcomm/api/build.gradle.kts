plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.appcomm.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.appcomm)
            }
        }
    }
}
