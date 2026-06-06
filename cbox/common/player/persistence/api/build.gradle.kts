plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    // SessionSnapshot is persisted as JSON in DataStore, so it carries @Serializable.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.player.persistence.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                // SessionType + RepeatMode travel on the snapshot; `api` so consumers that read
                // a SessionSnapshot get those enums resolvable transitively.
                api(projects.cbox.common.player.common.api)
                api(libs.kotlinx.serialization.core)
            }
        }
    }
}
