plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.kotlin.serialization)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.notif.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.notif.api)

                implementation(projects.cbox.common.scanner.api)
                implementation(projects.cbox.common.strings.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sage.common.logging)
                implementation(libs.sage.common.storage.common)
                implementation(libs.sage.common.ui.strings)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.notif.fake)
                implementation(projects.cbox.common.scanner.fake)
            }
        }
    }
}
