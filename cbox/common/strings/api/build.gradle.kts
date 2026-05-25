plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.strings.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.ui.strings)
            }
        }
    }
}
