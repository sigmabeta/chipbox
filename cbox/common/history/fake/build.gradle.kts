plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// Test double for PlaybackHistoryRepository — used by the recorder tests (to assert which plays got
// recorded) and by feature tests that construct a VM depending on the repository. JS variant
// exposed because apps/js binds this fake for the browser's empty playback-history graph.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.history.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.history.api)
                api(projects.cbox.common.models.api)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
