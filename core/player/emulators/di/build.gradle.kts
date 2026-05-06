plugins {
    id("sage.android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.di"
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    api(projects.cbox.common.player.emulators.fake.di)
    api(projects.core.player.emulators.twosf.di)
    api(projects.core.player.emulators.gba.di)
    api(projects.core.player.emulators.gme.di)
    api(projects.core.player.emulators.psf.di)
    api(projects.core.player.emulators.ssf.di)
}
