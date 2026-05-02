plugins {
    id("sage.android")
}

android {
    namespace = "net.sigmabeta.chipbox.styles"
}

dependencies {
    implementation(projects.core.colors)
    implementation(projects.core.drawables)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
