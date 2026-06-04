plugins {
    id("chipbox.feature.real")
    id("chipbox.kmp.test")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.gameDetail.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.director.api)

                implementation(projects.features.artistDetail.api)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.repository.fake)
                implementation(projects.cbox.common.player.director.fake)
            }
        }
    }
}
