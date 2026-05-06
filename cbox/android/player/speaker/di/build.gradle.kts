plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.speaker.di"
}

dependencies {
    api(projects.cbox.android.player.speaker.file.di)
    api(projects.cbox.android.player.speaker.real.di)
    api(projects.cbox.android.player.speaker.text.di)
    api(projects.cbox.common.player.speaker)
}
