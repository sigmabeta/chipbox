plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.di"
}

dependencies {
    api(projects.cbox.common.player.emulators.fake.di)
    api(projects.cbox.android.player.emulators.twosf.di)
    api(projects.cbox.android.player.emulators.gba.di)
    api(projects.cbox.android.player.emulators.gme.di)
    api(projects.cbox.android.player.emulators.psf.di)
    api(projects.cbox.android.player.emulators.ssf.di)
}
