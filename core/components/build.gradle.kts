plugins {
    id("sage.android")
    id("sage.compose.android")
}

android {
    namespace = "net.sigmabeta.chipbox.components"
}

dependencies {
    implementation(projects.cbox.android.imageLoading)
    implementation(projects.cbox.android.drawables)
    implementation(projects.cbox.common.models)
    implementation(projects.core.strings)

    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.accompanist:accompanist-insets:0.23.1")
}
