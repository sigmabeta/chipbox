plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsebyartist.real"
}

dependencies {
    api(projects.features.browseByArtist.api)
}
