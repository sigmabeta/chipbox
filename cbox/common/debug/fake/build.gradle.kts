plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// Test stub for DebugSettingsManager — single boolean preference behind a MutableStateFlow.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.debug.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.debug.api)

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
