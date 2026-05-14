plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.vgm.di"
}

dependencies {
    api(projects.cbox.android.player.emulators.vgm.api)
    api(projects.cbox.android.player.emulators.vgm.real)
}
