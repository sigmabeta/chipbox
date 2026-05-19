plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.settings.real"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.settings.api)

                implementation(libs.sage.common.storage.common)
            }
        }
    }
}
