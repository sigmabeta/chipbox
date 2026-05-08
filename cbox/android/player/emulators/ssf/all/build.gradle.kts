plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.ssf.all"
}

dependencies {
    api(projects.cbox.android.player.emulators.ssf.api)
    api(projects.cbox.android.player.emulators.ssf.real)
}
