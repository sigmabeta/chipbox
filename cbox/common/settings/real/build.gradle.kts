plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.settings.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.settings.api)

                implementation(libs.sage.common.storage.common)
            }
        }
    }
}
