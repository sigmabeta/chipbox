plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.gme.all"
}

dependencies {
    api(projects.cbox.android.player.emulators.gme.api)
    api(projects.cbox.android.player.emulators.gme.real)
}
