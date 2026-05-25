plugins {
    id("chipbox.screenshot")
}

dependencies {
    implementation(projects.features.nowPlaying.real)

    // The preview builds a NowPlayingModel directly, which references SourceInfo from sage images
    // (the :real module only depends on it via `implementation`, so it isn't transitive).
    implementation(libs.sage.common.images)
}
