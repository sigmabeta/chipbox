plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.buffer)

    implementation(projects.cbox.common.player.common)
}
