plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
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

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
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
