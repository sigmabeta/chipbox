plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.ui.chrome"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.ui.components)
            }
        }
    }
}
