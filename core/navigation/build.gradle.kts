plugins {
    id("sage.android")
    id("sage.compose.android")
}

android {
    namespace = "net.sigmabeta.chipbox.navigation"
}

dependencies {
    api(projects.features.artists)
    api(projects.features.artistDetail)
    api(projects.features.games)
    api(projects.features.gameDetail)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewModelCompose)

    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
}
