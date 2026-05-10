plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
}

android {
    namespace = "net.sigmabeta.chipbox.features.gamedetail.real"
}

dependencies {
    api(projects.features.gameDetail.api)
}
