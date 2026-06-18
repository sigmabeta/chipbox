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
    // The setlist panel's rows are sage NameCaptionValueListModels, built inline for the preview.
    implementation(libs.sage.common.ui.components)
}
