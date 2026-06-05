plugins {
    alias(chipbox.plugins.screenshot)
}

dependencies {
    implementation(projects.features.nowPlaying.real)

    // The preview builds a NowPlayingModel directly, which references SourceInfo from sage images
    // and RepeatMode from player.common (the :real module only depends on both via
    // `implementation`, so neither is transitive).
    implementation(libs.sage.common.images)
    implementation(projects.cbox.common.player.common.api)
}
