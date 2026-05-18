plugins {
    alias(libs.plugins.sage.screenshot)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsebyartist.screenshot"
}

dependencies {
    implementation(projects.features.browseByArtist.real)
    implementation(projects.cbox.android.ui.previews)
    implementation(projects.cbox.common.models.api)
}
