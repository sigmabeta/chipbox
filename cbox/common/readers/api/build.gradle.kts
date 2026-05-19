plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.readers.api"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.repository.api)

                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.utils.api)
                implementation(libs.sage.common.logging)
            }
        }
    }
}
