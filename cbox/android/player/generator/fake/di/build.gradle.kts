plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.fake.di"
}

dependencies {
    api(projects.cbox.common.player.generator.fake)
    api(projects.cbox.common.player.generator)
    api(projects.cbox.common.player.speaker)

    implementation(projects.cbox.common.player.common)
}
