plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
    alias(libs.plugins.metro)
}

// Second chipbox feature `:real` on sage.kmp (Settings was first — M9 slice 5c).
// LibraryViewModel + state/action are pure-Kotlin commonMain; LibraryRoute is androidMain
// because ChipboxListEntry is still androidMain (M9 slice 6 promotes it). Desktop gets its
// own route in apps/jvm once a CMP-friendly list scaffold lands.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.features.library.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.library.api)

                implementation(projects.cbox.android.ui.list.api)
                implementation(libs.sage.common.di)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)

                implementation(projects.features.browseAllTracks.api)
                implementation(projects.features.browseByArtist.api)
                implementation(projects.features.browseByGame.api)
                implementation(projects.features.browseByPlatform.api)

                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
            }
        }
    }
}
