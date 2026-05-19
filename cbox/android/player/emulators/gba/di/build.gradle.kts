plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.gba.di"
}

dependencies {
    api(projects.cbox.android.player.emulators.gba.api)
    api(projects.cbox.android.player.emulators.gba.real)
    // Android-only: packages the gba .so into the APK (KMP `:real` can't host CMake).
    runtimeOnly(projects.cbox.android.player.emulators.gba.native)
}
