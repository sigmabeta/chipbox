plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.scanner.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.models.api)
                api(libs.kotlinx.coroutines.core)

                implementation(libs.sage.common.logging)
            }
        }
    }
}
