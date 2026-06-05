plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.player.director.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.director.api)

                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.generator.api)
                implementation(projects.cbox.common.player.speaker.api)
                implementation(projects.cbox.common.repository.api)
                api(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.player.generator.fake)
                implementation(projects.cbox.common.player.speaker.fake)
                implementation(projects.cbox.common.repository.fake)
            }
        }
    }
}
