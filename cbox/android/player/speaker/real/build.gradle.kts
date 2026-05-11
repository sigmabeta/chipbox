plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.speaker.real"
}

dependencies {
    api(projects.cbox.common.player.speaker.api)
}
