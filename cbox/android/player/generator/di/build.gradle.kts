plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.di"
}

dependencies {
    api(projects.cbox.android.player.emulators.di)
    api(projects.cbox.android.player.generator.real)
    api(projects.cbox.common.player.generator.fake)
    api(projects.cbox.common.player.generator.api)
}
