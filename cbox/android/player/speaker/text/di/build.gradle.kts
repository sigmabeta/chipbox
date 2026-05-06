plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.speaker.text.di"
}

dependencies {
    api(projects.cbox.common.player.speaker.text)
    api(projects.cbox.common.player.speaker)
}
