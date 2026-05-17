plugins {
    alias(libs.plugins.sage.screenshot)
}

android {
    namespace = "net.sigmabeta.chipbox.features.search.screenshot"
}

dependencies {
    implementation(projects.features.search.real)
    implementation(projects.cbox.android.ui.previews)
    implementation(projects.cbox.common.models.api)

    // SearchContent's signature exposes ImmutableList<ListModel> (search renders its own
    // custom screen rather than going through ListScreenPreview).
    implementation(libs.sage.common.ui.components)
}
