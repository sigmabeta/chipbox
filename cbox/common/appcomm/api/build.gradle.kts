plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

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
