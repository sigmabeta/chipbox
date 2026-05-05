plugins {
    id("sage.android")
}

android {
    namespace = "net.sigmabeta.chipbox.strings"
}

dependencies {
    implementation(projects.cbox.android.colors)
    implementation(projects.core.drawables)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
