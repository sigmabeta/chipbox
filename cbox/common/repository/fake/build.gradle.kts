plugins {
    alias(libs.plugins.sage.kmp)
    id("chipbox.kmp.test")
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
                api(projects.cbox.common.models.api)
                implementation(projects.cbox.common.utils.api)
            }
        }
    }
}
