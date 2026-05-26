plugins {
    alias(libs.plugins.sage.kmp)
}

// Test stub for DebugInfoManager — a MutableStateFlow-backed double that lets tests push
// PlaybackDebugInfo snapshots into the screen's view-model.
kotlin {
    js { nodejs() }

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
