plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.director.di"
}

dependencies {
    api(projects.cbox.android.player.director.real.di)
}
