import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = chipboxNamespace()
    }

    sourceSets {
        named("jvmSharedMain") {
            // A very wrong version of coil was here for some reason.
            // Pull the newest one in instead.
        }
    }
}
