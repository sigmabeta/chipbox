plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.psf.all"
}

dependencies {
    api(projects.cbox.android.player.emulators.psf.api)
    api(projects.cbox.android.player.emulators.psf.real)
}
