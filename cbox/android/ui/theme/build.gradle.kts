plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "net.sigmabeta.chipbox.ui.theme"
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.material)
    api(libs.sage.android.ui.themes)
    implementation(projects.cbox.android.ui.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
