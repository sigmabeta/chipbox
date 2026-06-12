plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.player.speaker.di"
}

dependencies {
    api(projects.cbox.common.player.speaker.real)
    api(projects.cbox.common.player.speaker.fake)
    api(projects.cbox.common.player.speaker.api)
    implementation(projects.cbox.common.settings.api)
    implementation(projects.cbox.common.player.resampler.api)
}
