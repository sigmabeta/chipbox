plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.gba.di"
}

dependencies {
    api(projects.cbox.android.player.emulators.gba)
}
