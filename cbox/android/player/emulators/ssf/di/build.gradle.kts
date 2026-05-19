plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.ssf.di"
}

dependencies {
    api(projects.cbox.android.player.emulators.ssf.api)
    api(projects.cbox.android.player.emulators.ssf.real)
    // Android-only: packages the ssf .so into the APK (KMP `:real` can't host CMake).
    runtimeOnly(projects.cbox.android.player.emulators.ssf.native)
}
