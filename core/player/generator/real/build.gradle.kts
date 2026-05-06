plugins {
    id("sage.android")
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.real"
}

dependencies {
    api(projects.core.player.generator)
    api(projects.cbox.common.player.emulators)
    implementation(projects.core.player.speaker)
}
