plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.psf.di"
}

dependencies {
    api(projects.cbox.android.player.emulators.psf.api)
    api(projects.cbox.android.player.emulators.psf.real)
}
