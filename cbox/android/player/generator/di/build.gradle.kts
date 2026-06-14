plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.di"
}

dependencies {
    api(projects.cbox.common.player.generator.real)
    api(projects.cbox.common.player.generator.fake)
    api(projects.cbox.common.player.generator.api)
    // The debug "generator source" setting that selects Real vs the synth.
    implementation(projects.cbox.common.debug.api)
}
