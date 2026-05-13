plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
}

android {
    namespace = "net.sigmabeta.chipbox.ui.freeform"
}

dependencies {
    api(libs.sage.common.freeform)
    api(libs.sage.common.appcomm)
    api(libs.sage.common.ui.strings)
    api(libs.sage.common.ui.components)
    api(projects.cbox.common.appcomm.api)
    api(projects.cbox.android.ui.list.api)

    implementation(projects.cbox.android.ui.chrome.api)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
}
