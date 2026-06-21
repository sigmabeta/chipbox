plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.playlists.di"
}

dependencies {
    api(projects.cbox.common.playlists.api)
    api(projects.cbox.common.playlists.real)
}
