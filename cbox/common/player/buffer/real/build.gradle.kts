plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.player.buffer.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.buffer.api)

                implementation(projects.cbox.common.player.common.api)
                implementation(libs.sage.common.logging)
            }
        }
    }
}
