plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.vgm.all"
}

dependencies {
    api(projects.cbox.android.player.emulators.vgm.api)
    api(projects.cbox.android.player.emulators.vgm.real)
}
