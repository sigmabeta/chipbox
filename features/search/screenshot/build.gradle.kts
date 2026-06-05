plugins {
    alias(chipbox.plugins.screenshot)
}

dependencies {
    implementation(projects.features.search.real)

    // SearchContent's signature exposes ImmutableList<ListModel> (search renders its own
    // custom screen rather than going through ListScreenPreview).
    implementation(libs.sage.common.ui.components)
}
