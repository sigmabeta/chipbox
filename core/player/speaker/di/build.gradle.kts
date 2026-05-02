plugins {
    id("sage.android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.player.speaker.di"
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    api(projects.core.player.speaker.file.di)
    api(projects.core.player.speaker.real.di)
    api(projects.core.player.speaker.text.di)
    api(projects.core.player.speaker)
}
