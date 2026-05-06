plugins {
    id("sage.android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.player.director.real.di"
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    api(projects.core.player.director.real)
    implementation(projects.cbox.common.player.generator)
    implementation(projects.cbox.common.player.speaker)
}
