plugins {
    alias(libs.plugins.sage.screenshot)
}

android {
    namespace = "net.sigmabeta.chipbox.features.library.screenshot"
}

dependencies {
    implementation(projects.features.library.real)
    implementation(projects.cbox.android.ui.previews)
}
