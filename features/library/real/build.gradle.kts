plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

// LibraryViewModel, its state/action, and LibraryRoute are all pure-Kotlin commonMain — the
// screen is fully multiplatform now that ChipboxListEntry lives in commonMain.
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.library.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)

                implementation(projects.features.favorites.api)
                implementation(projects.features.playlists.api)
                implementation(projects.features.browseAllTracks.api)
                implementation(projects.features.browseByArtist.api)
                implementation(projects.features.browseByGame.api)
                implementation(projects.features.browseByPlatform.api)
            }
        }
    }
}
