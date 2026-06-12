plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.sage.di)
}

dependencies {
    api(projects.cbox.common.player.resampler.real)

    implementation(projects.cbox.common.player.resampler.api)
    implementation(projects.cbox.common.settings.api)
}
