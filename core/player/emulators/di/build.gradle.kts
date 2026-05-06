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
    api(projects.cbox.android.player.emulators.twosf.di)
    api(projects.cbox.android.player.emulators.gba.di)
    api(projects.cbox.android.player.emulators.gme.di)
    api(projects.cbox.android.player.emulators.psf.di)
    api(projects.cbox.android.player.emulators.ssf.di)
}
