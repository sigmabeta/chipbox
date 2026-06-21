plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.playlistDetail.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.ui.components.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.playlists.api)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.repository.fake)
                implementation(projects.cbox.common.playlists.fake)
            }
        }
    }
}
