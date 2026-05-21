plugins {
    alias(libs.plugins.sage.screenshot)
}

dependencies {
    implementation(projects.features.browseAllTracks.real)
}
