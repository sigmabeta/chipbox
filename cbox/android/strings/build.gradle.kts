plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.strings"
}

dependencies {
    implementation(projects.cbox.android.colors)
    implementation(projects.cbox.android.drawables)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
