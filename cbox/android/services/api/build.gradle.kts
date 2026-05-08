plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.services"
}

dependencies {
    api("androidx.media2:media2-common:1.3.0")

    implementation(projects.cbox.android.artworkprovider.api)
    implementation(projects.cbox.android.colors.api)
    implementation(projects.cbox.android.drawables.api)
    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.player.director.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.android.strings.api)
}
