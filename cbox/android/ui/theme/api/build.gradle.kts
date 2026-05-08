plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
}

android {
    namespace = "net.sigmabeta.chipbox.ui.theme"
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.material)
    api(libs.sage.android.ui.themes)
    implementation(projects.cbox.android.ui.fonts.api)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
