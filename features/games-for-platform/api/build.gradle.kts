plugins {
    id("chipbox.feature.api")
}

kotlin {
    js { nodejs() }

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(projects.cbox.common.models.api)
            }
        }
    }
}
