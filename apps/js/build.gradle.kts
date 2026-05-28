plugins {
    // Kotlin Multiplatform plugin is on the classpath via includeBuild("sage-build-logic"), so
    // apply it without a version (a versioned alias fails with "already on the classpath with an
    // unknown version"). Compose plugins do come from this project's catalog and work via alias.
    kotlin("multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
}

// Sister to apps/jvm + apps/android, but browser-only. sage.kmp / sage.compose.kmp aren't applied
// because they force `jvm()` + an Android library target and `jvmSharedMain` `dependsOn` edges
// that don't belong in a single-target executable. The Kotlin Multiplatform + Compose plugins
// above are what sage.compose.kmp adds on top of sage.kmp anyway — minus the Android pieces.

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "chipbox.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        named("jsMain") {
            dependencies {
                implementation(projects.cbox.common.appui.api)
                implementation(projects.cbox.common.strings.real)
                implementation(projects.cbox.common.strings.api)

                // Fakes for everything platform-dependent. No emulator C++ JNI, no filesystem,
                // no real audio output — silent playback through FakeSpeaker/FakeGenerator.
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.repository.fake)
                implementation(projects.cbox.common.contentsource.api)
                implementation(projects.cbox.common.contentsource.fake)
                implementation(projects.cbox.common.player.speaker.api)
                implementation(projects.cbox.common.player.speaker.fake)
                implementation(projects.cbox.common.player.generator.api)
                implementation(projects.cbox.common.player.generator.fake)
                implementation(projects.cbox.common.player.director.api)
                implementation(projects.cbox.common.player.director.fake)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.settings.api)
                implementation(projects.cbox.common.settings.fake)
                implementation(projects.cbox.common.debug.api)
                implementation(projects.cbox.common.debug.fake)
                implementation(projects.cbox.common.debugInfo.api)
                implementation(projects.cbox.common.debugInfo.fake)
                implementation(projects.cbox.common.scanner.api)
                implementation(projects.cbox.common.scanner.fake)
                implementation(projects.cbox.common.models.api)

                // VM supertypes — kept explicit so Kotlin/JS doesn't warn "Cannot access ...
                // supertype". The compiler still works without these (they're transitive), but
                // declaring direct deps for every type WebGraph constructs keeps incremental
                // builds clean.
                implementation(projects.cbox.common.ui.vm.api)
                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.ui.freeform.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(libs.sage.common.freeform)
                implementation(libs.sage.common.list)

                // Every feature :real module that has a non-assisted VM the WebMetroViewModelFactory
                // can construct directly. Detail screens (game/artist/games-for-platform) and the
                // folder picker use @Assisted constructors — those would need ViewModelAssistedFactory
                // wiring; clicking into them on JS will currently throw "Unknown model class".
                implementation(projects.cbox.common.playerStatus.api)
                implementation(projects.features.home.real)
                implementation(projects.features.library.real)
                implementation(projects.features.search.real)
                implementation(projects.features.settings.real)
                implementation(projects.features.manageLibrary.real)
                implementation(projects.features.rescanStatus.real)
                implementation(projects.features.nowPlaying.real)
                implementation(projects.features.browseAllTracks.real)
                implementation(projects.features.browseByArtist.real)
                implementation(projects.features.browseByGame.real)
                implementation(projects.features.browseByPlatform.real)
                implementation(projects.features.playbackStatus.real)

                implementation(libs.sage.common.di)
                implementation(libs.sage.common.logging)
                implementation(libs.sage.common.appinfo)
                implementation(libs.sage.common.ui.perfCompose)
                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)

                implementation(libs.kotlinx.coroutines.core)
                // FolderPicker's OkioFolderLister injects okio.FileSystem; the browser doesn't have
                // a real filesystem, so a FakeFileSystem keeps the graph closed. The JS folder
                // picker Route is a stub anyway.
                implementation(libs.okio)
                implementation(libs.okio.fakefilesystem)

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui)

                implementation(libs.androidx.lifecycle.viewmodel)
            }
        }
    }
}
