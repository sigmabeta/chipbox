plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.player.generator.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.common.api)
                api(projects.cbox.common.player.buffer.api)
                api(projects.cbox.common.player.cache.api)
                api(projects.cbox.common.repository.api)
                api(projects.cbox.common.contentsource.api)
                api(libs.kotlinx.coroutines.core)
                api(libs.sage.common.logging)
            }
        }
    }
}
