plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.features.library.real"
}

dependencies {
    api(projects.features.library.api)

    implementation(projects.cbox.android.ui.list.api)
    implementation(projects.cbox.common.appcomm.api)
    implementation(projects.cbox.common.strings.api)

    implementation(projects.features.browseAllTracks.api)
    implementation(projects.features.browseByArtist.api)
    implementation(projects.features.browseByGame.api)

    implementation(libs.androidx.hilt.navigation.compose)
}
