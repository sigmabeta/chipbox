plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.usf.di"
}

dependencies {
    api(projects.cbox.android.player.emulators.usf.api)
    api(projects.cbox.android.player.emulators.usf.real)
}
