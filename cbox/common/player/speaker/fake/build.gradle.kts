plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.player.speaker.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.speaker.api)

                implementation(projects.cbox.common.utils.api)
                implementation(libs.sage.common.logging)
                api(libs.okio)
            }
        }

        named("jvmSharedTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
