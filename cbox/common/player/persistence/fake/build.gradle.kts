plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// In-memory PlaybackSessionStore for tests: save/clear mutate a held snapshot and record calls.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.player.persistence.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.persistence.api)

                // Resolves the coroutines opt-in marker the re-exported player types carry, matching
                // the other player :fake modules.
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
