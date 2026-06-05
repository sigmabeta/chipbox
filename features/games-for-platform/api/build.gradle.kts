plugins {
    alias(chipbox.plugins.feature.api)
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(projects.cbox.common.models.api)
            }
        }
    }
}
