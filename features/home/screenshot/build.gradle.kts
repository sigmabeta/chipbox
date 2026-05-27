plugins {
    id("chipbox.screenshot")
}

dependencies {
    implementation(projects.features.home.real)

    // Home previews build sage list models (GridImageListModel) inline because each
    // HomeModule owns its own item construction — without this, only the State pipeline
    // (transitively available through :previews) would be visible.
    implementation(libs.sage.common.ui.components)
    // ChipboxAction (HomeAction's parent) — without it the compiler can't see GameClicked
    // is-a SageAction when assigning it as a click action.
    implementation(projects.cbox.common.appcomm.api)
}
