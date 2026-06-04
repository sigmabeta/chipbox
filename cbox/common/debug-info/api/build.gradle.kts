plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.debug.info.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.models.api)
                api(projects.cbox.common.player.common.api)
                api(projects.cbox.common.player.director.api)
                api(projects.cbox.common.player.generator.api)
                api(projects.cbox.common.player.speaker.api)
                api(projects.cbox.common.player.buffer.api)
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
