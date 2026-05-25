plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
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
