plugins {
    alias(libs.plugins.sage.kmp)
}

// Test/stub fakes implementing PcmTrackSource. Production fakes (the synth-backed
// cache sources) live in :cbox:common:player:cache:real because they're tied to
// PcmCacheFile internals; these are the call-recording, scriptable doubles tests use.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.cache.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.cache.api)

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
