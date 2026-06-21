plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.playlists.api)
    api(projects.cbox.common.playlists.real)

    implementation(libs.sage.common.logging)
}
