plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// Test stub for ChipboxSettingsManager — every getter is a MutableStateFlow tests can drive
// directly via the matching setX, plus a record of every write call.
kotlin {
    android {
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
