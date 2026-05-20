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
    // ChipboxLight / ChipboxDark / ChipboxMenu color schemes — moved into a shared KMP
    // module so the JVM/desktop entry can consume the same palette. Re-exported via api()
    // because AppTheme.kt in this module resolves them through this module's classpath.
    api(projects.cbox.common.ui.theme.api)
    implementation(projects.cbox.common.ui.fonts.api)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
