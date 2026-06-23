plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.playlists.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.playlists.api)

                implementation(projects.features.playlistDetail.api)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.playlists.fake)
            }
        }
    }
}
