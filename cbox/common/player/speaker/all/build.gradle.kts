plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.speaker.api)
    api(projects.cbox.common.player.speaker.fake)
}
