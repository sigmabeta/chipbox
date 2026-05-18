plugins {
    alias(libs.plugins.sage.screenshot)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsebygame.screenshot"
}

dependencies {
    implementation(projects.features.browseByGame.real)
    implementation(projects.cbox.android.ui.previews)
    implementation(projects.cbox.common.models.api)
}
