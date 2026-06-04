plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// Test stub for DebugInfoManager — a MutableStateFlow-backed double that lets tests push
// PlaybackDebugInfo snapshots into the screen's view-model.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.debuginfo.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.debugInfo.api)

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
