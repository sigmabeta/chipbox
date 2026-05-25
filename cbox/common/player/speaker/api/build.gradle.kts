plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.speaker.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.common.api)
                api(projects.cbox.common.player.buffer.api)
                api(libs.kotlinx.coroutines.core)
                api(libs.sage.common.logging)
            }
        }
    }
}
