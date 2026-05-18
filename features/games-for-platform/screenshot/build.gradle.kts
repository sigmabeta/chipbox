plugins {
    alias(libs.plugins.sage.screenshot)
}

android {
    namespace = "net.sigmabeta.chipbox.features.gamesforplatform.screenshot"
}

dependencies {
    implementation(projects.features.gamesForPlatform.real)
    implementation(projects.cbox.android.ui.previews)
    implementation(projects.cbox.common.models.api)
}
