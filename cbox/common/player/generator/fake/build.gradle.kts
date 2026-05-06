plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.generator)

    implementation(projects.cbox.common.player.emulators.fake)
}
