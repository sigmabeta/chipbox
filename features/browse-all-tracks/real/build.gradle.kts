plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.browseAllTracks.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.director.api)
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
