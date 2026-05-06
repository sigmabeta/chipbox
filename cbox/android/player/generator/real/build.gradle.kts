plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.real"
}

dependencies {
    api(projects.cbox.common.player.generator)
    api(projects.cbox.common.player.emulators)

    implementation(projects.cbox.common.player.speaker)
}
