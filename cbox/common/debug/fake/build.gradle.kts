plugins {
    alias(libs.plugins.sage.kmp)
}

// Test stub for DebugSettingsManager — single boolean preference behind a MutableStateFlow.
kotlin {
    js { nodejs() }

    androidLibrary {
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
