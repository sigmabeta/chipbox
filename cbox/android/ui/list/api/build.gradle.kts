plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
}

android {
    namespace = "net.sigmabeta.chipbox.ui.list"
}

dependencies {
    api(libs.sage.common.list)
    api(libs.sage.common.appcomm)
    api(libs.sage.common.ui.strings)
    api(libs.sage.common.ui.components)

    implementation(libs.sage.android.ui.list)
    implementation(projects.cbox.android.ui.components.api)

    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
}
