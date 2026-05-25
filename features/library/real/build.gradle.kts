plugins {
    id("chipbox.feature.real")
}

// LibraryViewModel + state/action are pure-Kotlin commonMain; LibraryRoute is androidMain
// because ChipboxListEntry is still androidMain (M9 slice 6 promotes it). Desktop gets its
// own route in apps/jvm once a CMP-friendly list scaffold lands.
kotlin {
    js { nodejs() }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.library.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)

                implementation(projects.features.browseAllTracks.api)
                implementation(projects.features.browseByArtist.api)
                implementation(projects.features.browseByGame.api)
                implementation(projects.features.browseByPlatform.api)
            }
        }
    }
}
