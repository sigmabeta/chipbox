plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsebygame.real"
}

dependencies {
    api(projects.features.browseByGame.api)
}
