plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.cache.di"
}

dependencies {
    api(projects.cbox.common.player.cache.api)
    api(projects.cbox.common.player.cache.real)
    api(projects.cbox.android.player.emulators.di)

    implementation(projects.cbox.common.contentsource.api)
}
