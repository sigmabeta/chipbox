plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.player.emulators.api)
    api(projects.cbox.common.player.emulators.fake)
}
