plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.usf.all"
}

dependencies {
    api(projects.cbox.android.player.emulators.usf.api)
    api(projects.cbox.android.player.emulators.usf.real)
}
