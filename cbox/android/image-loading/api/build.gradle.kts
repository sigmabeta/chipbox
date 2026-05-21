plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.image_loading"
    }

    sourceSets {
        named("jvmSharedMain") {
            // A very wrong version of coil was here for some reason.
            // Pull the newest one in instead.
        }
    }
}
