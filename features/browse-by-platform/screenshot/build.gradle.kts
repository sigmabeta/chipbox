plugins {
    alias(chipbox.plugins.screenshot)
}

dependencies {
    implementation(projects.features.browseByPlatform.real)
}
