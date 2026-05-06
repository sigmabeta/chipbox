plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.emulators.fake.di"
}

dependencies {
    api(projects.cbox.common.player.emulators.fake)
}
