plugins {
    id("chipbox.feature.real")
    id("chipbox.kmp.test")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.browseByGame.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.models.api)

                implementation(projects.features.gameDetail.api)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.repository.fake)
            }
        }
    }
}
