plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// Test-only fakes implementing the Director interface — primarily for ViewModel tests that
// consume the metadata / playback / session / error streams without dragging a real
// generator+speaker+repository pipeline in.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.player.director.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.director.api)
                api(projects.cbox.common.player.common.api)
                api(projects.cbox.common.models.api)

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
