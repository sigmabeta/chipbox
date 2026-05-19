plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.image_loading"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api("io.coil-kt:coil:2.4.0")
                api("io.coil-kt:coil-compose:2.4.0")
            }
        }
    }
}
