plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.models.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.strings.api)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
