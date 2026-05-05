plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.drawables"
}

dependencies {
    implementation(projects.cbox.android.colors)
}
