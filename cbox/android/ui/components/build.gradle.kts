plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
}

dependencies {
    api(libs.sage.common.ui.components)

    implementation(libs.sage.common.appcomm)
    implementation(libs.sage.common.images)

    implementation(libs.sage.android.bitmaps)
    implementation(libs.sage.android.perf)
    implementation(libs.sage.android.ui.icons)

    implementation(projects.cbox.android.images)
    implementation(projects.cbox.android.strings)
    implementation(projects.cbox.android.ui.fonts)
    implementation(projects.cbox.android.ui.theme)

    implementation(libs.kotlin.reflect)
    implementation(libs.material)
}

android {
    namespace = "net.sigmabeta.chipbox.ui.components"
}