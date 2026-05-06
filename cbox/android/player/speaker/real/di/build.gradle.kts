plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.speaker.real.di"
}

dependencies {
    api(projects.cbox.android.player.speaker.real)
    api(projects.cbox.common.player.speaker)

    implementation(projects.cbox.common.player.common)
}
