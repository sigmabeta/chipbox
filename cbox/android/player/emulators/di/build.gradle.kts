plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.di"
}

dependencies {
    implementation(projects.cbox.common.player.emulators.api)
    implementation(projects.cbox.common.player.emulators.fake)
    implementation(projects.cbox.common.player.emulators.gba.real)
    implementation(projects.cbox.common.player.emulators.gme.real)
    implementation(projects.cbox.common.player.emulators.ncsf.real)
    implementation(projects.cbox.common.player.emulators.psf.real)
    implementation(projects.cbox.common.player.emulators.ssf.real)
    implementation(projects.cbox.common.player.emulators.twosf.real)
    implementation(projects.cbox.common.player.emulators.usf.real)
    implementation(projects.cbox.common.player.emulators.vgm.real)
    implementation(projects.cbox.common.player.emulators.vgmstream.real)
}
