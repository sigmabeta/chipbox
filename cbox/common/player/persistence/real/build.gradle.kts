plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.kotlin.serialization)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.player.persistence.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.persistence.api)

                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.director.api)
                implementation(projects.cbox.common.models.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sage.common.storage.common)
                implementation(libs.sage.common.logging)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.player.persistence.fake)
                implementation(projects.cbox.common.player.director.fake)
            }
        }
    }
}
