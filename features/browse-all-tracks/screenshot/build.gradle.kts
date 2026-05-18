plugins {
    alias(libs.plugins.sage.screenshot)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsealltracks.screenshot"
}

dependencies {
    implementation(projects.features.browseAllTracks.real)
    implementation(projects.cbox.android.ui.previews)
    implementation(projects.cbox.common.models.api)
}
