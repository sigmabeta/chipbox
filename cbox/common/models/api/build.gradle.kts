plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.models.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.strings.api)
            }
        }
    }
}
