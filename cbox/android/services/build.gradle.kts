plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.services"
}

dependencies {
    api("androidx.media2:media2-common:1.3.0")

    implementation(projects.cbox.android.colors)
    implementation(projects.cbox.android.drawables)
    implementation(projects.cbox.common.player.common)
    implementation(projects.cbox.common.player.director)
    implementation(projects.cbox.common.repository)
    implementation(projects.cbox.android.strings)
}
