plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.styles"
}

dependencies {
    implementation(projects.cbox.android.colors.api)
    implementation(projects.cbox.android.drawables.api)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
