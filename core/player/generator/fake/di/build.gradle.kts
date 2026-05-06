plugins {
    id("sage.android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.fake.di"
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    api(projects.core.player.generator.fake)
    api(projects.core.player.generator)
    api(projects.cbox.common.player.speaker)
    implementation(projects.cbox.common.player.common)
}
