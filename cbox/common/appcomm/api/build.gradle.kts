plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.appcomm.api"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(libs.sage.common.appcomm)
            }
        }
    }
}
