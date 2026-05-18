plugins {
    alias(libs.plugins.sage.screenshot)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsebyplatform.screenshot"
}

dependencies {
    implementation(projects.features.browseByPlatform.real)
    implementation(projects.cbox.android.ui.previews)
    implementation(projects.cbox.common.models.api)
}
