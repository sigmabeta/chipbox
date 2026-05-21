plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.sigmabeta.chipbox.appui"
}

dependencies {
    implementation(projects.cbox.android.playerStatus.api)
    implementation(projects.cbox.android.ui.chrome.api)
    implementation(projects.cbox.android.ui.components.api)
    implementation(projects.cbox.android.ui.list.api)
    implementation(projects.cbox.android.ui.theme.api)
    implementation(projects.cbox.common.appcomm.api)
    implementation(projects.cbox.common.strings.api)

    implementation(projects.features.library.api)
    implementation(projects.features.library.real)
    implementation(projects.features.nowPlaying.api)
    implementation(projects.features.nowPlaying.real)
    // features.playbackStatus deps dropped during the M5c Hilt → Metro VM sweep.
    // PlaybackStatusEntryPoint was a variant-selected hook (debug=real, release=fake)
    // and Metro 1.1.1 doesn't aggregate @ContributesTo across variant-specific
    // debug/release implementation chains reliably (see ChipboxNavHost.kt note).
    // Re-add once playback-status' variant aggregation has a fix.
    implementation(projects.features.search.api)
    implementation(projects.features.search.real)
    implementation(projects.features.settings.api)
    implementation(projects.features.settings.real)
    implementation(projects.features.browseAllTracks.api)
    implementation(projects.features.browseAllTracks.real)
    implementation(projects.features.browseByArtist.api)
    implementation(projects.features.browseByArtist.real)
    implementation(projects.features.browseByGame.api)
    implementation(projects.features.browseByGame.real)
    implementation(projects.features.browseByPlatform.api)
    implementation(projects.features.browseByPlatform.real)
    implementation(projects.features.gamesForPlatform.api)
    implementation(projects.features.gamesForPlatform.real)
    implementation(projects.features.gameDetail.api)
    implementation(projects.features.gameDetail.real)
    implementation(projects.features.artistDetail.api)
    implementation(projects.features.artistDetail.real)

    implementation(libs.sage.android.ui.list)

    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.serialization.core)
}
