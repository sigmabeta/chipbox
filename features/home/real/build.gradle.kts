plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.home.api)

                implementation(projects.cbox.common.ui.components.api)
                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.notif.api)
                // Host-policy flag: the browser's library is server-owned, so the empty state must
                // not offer folder/rescan CTAs.
                implementation(projects.cbox.common.capabilities.api)
                implementation(projects.cbox.common.notif.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.history.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.scanner.api)
                implementation(projects.cbox.common.contentsource.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.director.api)

                implementation(projects.features.artistDetail.api)
                implementation(projects.features.folderPicker.api)
                implementation(projects.features.gameDetail.api)
                implementation(projects.features.nowPlaying.api)
                implementation(projects.features.rescanStatus.api)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.notif.fake)
                implementation(projects.cbox.common.repository.fake)
                implementation(projects.cbox.common.history.fake)
                implementation(projects.cbox.common.contentsource.fake)
                implementation(projects.cbox.common.scanner.fake)
                implementation(projects.cbox.common.player.director.fake)
            }
        }
    }
}
