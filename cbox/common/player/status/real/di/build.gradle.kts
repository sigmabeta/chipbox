plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.status.real.di"
}

dependencies {
    api(projects.cbox.common.player.status.real)
    api(projects.cbox.common.player.status)
}
