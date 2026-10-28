plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    // The sink-init lifecycle is pure logic and runs under jsNodeTest as well as jvmTest.
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.player.speaker.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.common.api)
                api(projects.cbox.common.player.resampler.api)
                api(projects.cbox.common.player.buffer.api)
                api(libs.kotlinx.coroutines.core)
                api(libs.sage.common.logging)
            }
        }
    }
}
