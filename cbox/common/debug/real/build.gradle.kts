plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.debug.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.debug.api)

                implementation(libs.sage.common.storage.common)
            }
        }
    }
}
