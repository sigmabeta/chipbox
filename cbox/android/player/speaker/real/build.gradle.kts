plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.speaker.real"
}

dependencies {
    api(projects.cbox.common.player.speaker.api)

    implementation("androidx.media2:media2-common:1.3.0")
    implementation(libs.sage.common.logging)
}
