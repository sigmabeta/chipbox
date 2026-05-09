plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.speaker.api)

    implementation(libs.sage.common.logging)
}
