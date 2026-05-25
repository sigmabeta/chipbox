plugins {
    alias(libs.plugins.sage.feature.api)
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
