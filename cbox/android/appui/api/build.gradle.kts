plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.appui"
}

dependencies {
    // PlayerStatusViewModel + the feature `:real` modules below need `api()` (not
    // `implementation()`) so their @ContributesIntoMap VMs are aggregated into
    // ChipboxAppGraph when apps/android compiles. Metro's FIR pass discovers hints
    // on the compile classpath, but Gradle's `implementation` configuration hides
    // transitive types from second-level consumers — Metro then doesn't see those
    // hints in apps/android. Promoting these to `api` re-exposes them. Discovered
    // when LibraryViewModel started crashing at runtime with `Unknown model class`
    // (M9 slice 5d of docs/kmp-migration.md); confirmed every non-Settings feature
    // VM had the same latent bug from the Metro M5d sweep — only SettingsViewModel
    // and ChipboxAppUiViewModel (one direct hop from apps/android) were aggregated.
    api(projects.cbox.android.playerStatus.api)
    implementation(projects.cbox.android.ui.chrome.api)
    implementation(projects.cbox.android.ui.components.api)
    implementation(projects.cbox.android.ui.list.api)
    implementation(projects.cbox.android.ui.theme.api)
    implementation(projects.cbox.common.appcomm.api)
    implementation(projects.cbox.common.strings.api)
    // Needed for `GamesForPlatformDeepScreen(val platform: Platform)` — the typed Voyager
    // Screen that carries the route arg now that AndroidX nav's typesafe routing is gone.
    implementation(projects.cbox.common.models.api)

    implementation(projects.features.library.api)
    api(projects.features.library.real)
    implementation(projects.features.nowPlaying.api)
    api(projects.features.nowPlaying.real)
    // features.playbackStatus deps dropped during the M5c Hilt → Metro VM sweep.
    // PlaybackStatusEntryPoint was a variant-selected hook (debug=real, release=fake)
    // and Metro 1.1.1 doesn't aggregate @ContributesTo across variant-specific
    // debug/release implementation chains reliably (see ChipboxNavHost.kt note).
    // Re-add once playback-status' variant aggregation has a fix.
    implementation(projects.features.search.api)
    api(projects.features.search.real)
    implementation(projects.features.settings.api)
    api(projects.features.settings.real)
    implementation(projects.features.browseAllTracks.api)
    api(projects.features.browseAllTracks.real)
    implementation(projects.features.browseByArtist.api)
    api(projects.features.browseByArtist.real)
    implementation(projects.features.browseByGame.api)
    api(projects.features.browseByGame.real)
    implementation(projects.features.browseByPlatform.api)
    api(projects.features.browseByPlatform.real)
    implementation(projects.features.gamesForPlatform.api)
    api(projects.features.gamesForPlatform.real)
    implementation(projects.features.gameDetail.api)
    api(projects.features.gameDetail.real)
    implementation(projects.features.artistDetail.api)
    api(projects.features.artistDetail.real)

    implementation(libs.sage.android.ui.list)

    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    // Voyager — single Compose Multiplatform nav stack shared with apps/jvm. Replaces
    // androidx.navigation:navigation-compose (the AndroidX CMP fork publishes JVM stubs
    // only). Three artifacts: navigator (per-stack push/pop), tab-navigator (per-tab
    // back stacks for Library/Search/Settings — saveState + restoreState equivalent),
    // transitions (SlideTransition between pushed screens).
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.tab.navigator)
    implementation(libs.voyager.transitions)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}
