plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.readers.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.repository.api)

                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.utils.api)
                implementation(libs.sage.common.logging)
            }
        }

        named("jvmSharedTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.sage.common.logging)
            }
        }
    }
}
