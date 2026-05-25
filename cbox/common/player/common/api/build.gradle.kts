plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.player.common"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.logging)
            }
        }
    }
}
