plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.buffer.real.di"
}

dependencies {
    api(projects.cbox.common.player.buffer.real)
}
