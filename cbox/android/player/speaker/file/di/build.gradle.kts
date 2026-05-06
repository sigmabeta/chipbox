plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.speaker.file.di"
}

dependencies {
    api(projects.cbox.common.player.speaker.file)
    api(projects.cbox.common.player.speaker)
}
