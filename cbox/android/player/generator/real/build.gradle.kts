plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.real"
}

dependencies {
    api(projects.cbox.common.player.generator.api)
    api(projects.cbox.common.player.emulators.api)
    api(projects.cbox.common.player.cache.real)

    implementation(projects.cbox.common.player.speaker.api)
}
