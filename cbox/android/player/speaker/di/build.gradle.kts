plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.speaker.di"
}

dependencies {
    api(projects.cbox.android.player.speaker.real)
    api(projects.cbox.common.player.speaker.fake)
    api(projects.cbox.common.player.speaker.api)
}
