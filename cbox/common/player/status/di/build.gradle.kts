plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.status.di"
}

dependencies {
    api(projects.cbox.common.player.status.real.di)
    api(projects.cbox.common.player.status)
}
