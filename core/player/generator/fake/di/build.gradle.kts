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
    api(projects.core.player.speaker)
    implementation(projects.core.player.common)
}
