plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.real.di"
}

dependencies {
    api(projects.cbox.android.player.emulators.di)
    api(projects.cbox.android.player.generator.real)
}
