plugins {
    alias(libs.plugins.sage.kmp)
}

// Playback-history contracts: the @Dao interfaces for the separate HistoryDatabase plus the
// PlaybackHistoryRepository / PlaybackHistoryRecorder interfaces. Annotations only (room-common) +
// the entity types + Track, so no room-runtime/KSP here — history/real owns the @Database and runs
// room-compiler against these. No js() gate: nothing on the JS target consumes playback history.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.history.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.room.common)
                api(projects.cbox.common.entities.api)
                api(projects.cbox.common.models.api)
                // Flow appears in the DAO + repository public API, so expose it transitively.
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
