plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.favorites.di"
}

dependencies {
    api(projects.cbox.common.favorites.api)
    api(projects.cbox.common.favorites.real)
}
