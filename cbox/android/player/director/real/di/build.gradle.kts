plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.director.real.di"
}

dependencies {
    api(projects.cbox.common.player.director.real)

    implementation(projects.cbox.common.player.generator)
    implementation(projects.cbox.common.player.speaker)
}
