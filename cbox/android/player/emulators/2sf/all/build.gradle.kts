plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.twosf.all"
}

dependencies {
    api(projects.cbox.android.player.emulators.twosf.api)
    api(projects.cbox.android.player.emulators.twosf.real)
}
