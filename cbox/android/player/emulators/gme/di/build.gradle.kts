plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.gme.di"
}

dependencies {
    api(projects.cbox.android.player.emulators.gme.api)
    api(projects.cbox.android.player.emulators.gme.real)
    // Android-only: packages the GME .so into the APK (KMP `:real` can't host CMake).
    runtimeOnly(projects.cbox.android.player.emulators.gme.native)
}
