plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.player.generator.api)

    implementation(projects.cbox.common.player.emulators.fake)
}
