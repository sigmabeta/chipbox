plugins {
    id("sage.android")
}

android {
    namespace = "net.sigmabeta.chipbox.styles"
}

dependencies {
    implementation(projects.cbox.android.colors)
    implementation(projects.cbox.android.drawables)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
