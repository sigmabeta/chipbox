plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.repository.fake"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.utils.api)
            }
        }
    }
}
