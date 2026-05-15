plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.buffer.api)

    implementation(projects.cbox.common.player.common.api)
    implementation(libs.sage.common.logging)
}
