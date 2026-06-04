plugins {
    id("chipbox.feature.api")
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
