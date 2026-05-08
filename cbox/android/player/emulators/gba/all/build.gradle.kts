plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.gba.all"
}

dependencies {
    api(projects.cbox.android.player.emulators.gba.api)
    api(projects.cbox.android.player.emulators.gba.real)
}
