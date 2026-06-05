plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    android {
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
