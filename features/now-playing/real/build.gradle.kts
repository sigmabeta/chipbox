plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.nowPlaying.api)

                // Route keys for the LINKS/ARTISTS context-menu navigation targets.
                implementation(projects.features.gameDetail.api)
                implementation(projects.features.gamesForPlatform.api)
                implementation(projects.features.artistDetail.api)
                implementation(projects.features.playlists.api)

                implementation(projects.cbox.common.ui.freeform.api)
                implementation(projects.cbox.common.ui.components.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.director.api)
                // EXPERIMENT: inline setlist mode resolves queue ids to Track metadata.
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.favorites.api)

                implementation(libs.sage.common.appcomm)
                // EXPERIMENT: drag-to-reorder for the inline setlist (same lib sage's
                // ReorderableScreen uses). Productionizing would extract a shared reorderable
                // column rather than spread this dependency.
                implementation(libs.reorderable)
                implementation(libs.sage.common.freeform)
                implementation(libs.sage.common.images)
                implementation(libs.sage.common.ui.components)
                implementation(libs.sage.common.ui.strings)
                // Icon.vector() renders the sage Icon set for the whole transport/error UI.
                implementation(libs.sage.common.ui.iconsReal)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.player.director.fake)
                implementation(projects.cbox.common.repository.fake)
                implementation(projects.cbox.common.favorites.fake)
            }
        }
    }
}
