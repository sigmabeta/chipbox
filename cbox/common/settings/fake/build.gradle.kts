plugins {
    alias(libs.plugins.sage.kmp)
}

// Test stub for ChipboxSettingsManager — every getter is a MutableStateFlow tests can drive
// directly via the matching setX, plus a record of every write call.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.settings.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.settings.api)

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
