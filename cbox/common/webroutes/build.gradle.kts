plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(chipbox.plugins.kmp.test)
}

// Pure route <-> URL mapping for the browser client (ADR-0003): no Compose, no Voyager, no
// kotlinx.browser. The same rules therefore run in `jsNodeTest` (Node) and `jvmTest`.
//
// `sage.compose.kmp` must NOT be applied here: Compose's JS distribution ships Skiko only for
// browsers/WASM, so a Node import fails before a test can run (see
// arch-docs/architecture/web-app.md, Testing).
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.webroutes"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                // AppCapabilities + the shell's allowsNavigation policy: the decoder must apply the
                // same host policy as the in-app sinks instead of publishing hidden routes.
                api(projects.cbox.common.capabilities.api)
                api(projects.cbox.common.models.api)

                // Route keys (each feature's @Serializable data object/data class). The codec only
                // maps them to paths; it never imports the feature UI or Voyager.
                implementation(projects.features.home.api)
                implementation(projects.features.library.api)
                implementation(projects.features.search.api)
                implementation(projects.features.settings.api)
                implementation(projects.features.errorLog.api)
                implementation(projects.features.favorites.api)
                implementation(projects.features.playlists.api)
                implementation(projects.features.playlistDetail.api)
                implementation(projects.features.browseByGame.api)
                implementation(projects.features.browseByArtist.api)
                implementation(projects.features.browseByPlatform.api)
                implementation(projects.features.browseAllTracks.api)
                implementation(projects.features.gameDetail.api)
                implementation(projects.features.artistDetail.api)
                implementation(projects.features.gamesForPlatform.api)
                implementation(projects.features.tracksForArtist.api)
                implementation(projects.features.nowPlaying.api)

                // Admin/debug-only route keys are deliberately *not* mapped. They are dependencies
                // so `encode` can recognise and refuse them explicitly (rather than falling into a
                // silent catch-all), matching the shell's capability guard.
                implementation(projects.features.manageLibrary.api)
                implementation(projects.features.folderPicker.api)
                implementation(projects.features.rescanStatus.api)
                implementation(projects.features.playbackStatus.api)
                implementation(projects.features.crashLog.api)
                implementation(projects.features.componentLibrary.api)
            }
        }
    }
}
